package org.elpaca_metagraph.shared_data.combiners

import cats.syntax.all._
import eu.timepit.refined.types.numeric.NonNegLong
import monocle.Monocle.toAppliedFocusOps
import org.elpaca_metagraph.shared_data.Utils._
import org.elpaca_metagraph.shared_data.app.ApplicationConfig
import org.elpaca_metagraph.shared_data.app.ApplicationConfig.SearchInfo
import org.elpaca_metagraph.shared_data.types.DataUpdates.YouTubeUpdate
import org.elpaca_metagraph.shared_data.types.Lattice.RewardInfo
import org.elpaca_metagraph.shared_data.types.States.{DataSource, DataSourceType, YouTubeDataSource}
import org.elpaca_metagraph.shared_data.types.YouTube.{YouTubeDataSourceAddress, YouTubeRewardInfo}
import org.tessellation.schema.epoch.EpochProgress

object YouTubeCombiner {
  def updateYoutubeRewardsOlderThanOneDay(
    currentCalculatedState: Map[DataSourceType, DataSource],
    currentEpochProgress: EpochProgress
  ): Map[DataSourceType, DataSource] = {
    val youtubeDatasource = getYouTubeDatasource(currentCalculatedState)
    val updatedDataSourceAddress = youtubeDatasource.existingWallets.foldLeft(youtubeDatasource.existingWallets) { (acc, current) =>
      val (address, youtubeDataSourceAddress) = current
      val updatedRewards = youtubeDataSourceAddress.addressRewards.foldLeft(youtubeDataSourceAddress.addressRewards) { (innerAcc, innerCurrent) =>
        val (searchText, rewardInfo) = innerCurrent
        if (isNewDay(rewardInfo.dailyEpochProgress, currentEpochProgress)) {
          innerAcc.updated(searchText, rewardInfo.focus(_.dailyPostsNumber).replace(0))
        } else {
          innerAcc
        }
      }

      acc.updated(address, youtubeDataSourceAddress.focus(_.addressRewards).replace(updatedRewards))
    }

    currentCalculatedState.updated(
      DataSourceType.YouTube,
      youtubeDatasource.focus(_.existingWallets).replace(updatedDataSourceAddress)
    )
  }

  def updateYouTubeState(
    currentCalculatedState: Map[DataSourceType, DataSource],
    currentEpochProgress: EpochProgress,
    youTubeUpdate: YouTubeUpdate,
    applicationConfig: ApplicationConfig
  ): YouTubeDataSource = {
    val searchInformation = applicationConfig.youtubeDaemon.searchInformation
    val youtubeDatasource = getYouTubeDatasource(currentCalculatedState)
    val youTubeDataSourceAddress = youtubeDatasource.existingWallets.getOrElse(youTubeUpdate.address, YouTubeDataSourceAddress())

    searchInformation
      .find(_.text.toLowerCase == youTubeUpdate.searchText.toLowerCase)
      .fold(youtubeDatasource) { searchTerm =>
        val updatedRewardInfo = youTubeDataSourceAddress.addressRewards.get(youTubeUpdate.searchText.toLowerCase).map { data =>
          val currentRewardInfo = if (data.rewardCandidates.isEmpty) {
            data.focus(_.rewardCandidates).replace(Some(List.empty))
          } else data

          val rewardInfo = if (isRewardInfoExpired(currentRewardInfo)) {
            currentRewardInfo
              .focus(_.dailyEpochProgress).replace(currentRewardInfo.epochProgressToReward)
              .focus(_.dailyPostsNumber).replace(searchTerm.maxPerDay)
          } else if (isNewDay(currentRewardInfo.epochProgressToReward, currentEpochProgress)) {
            val firstRewardOfTheDay = currentRewardInfo
              .focus(_.dailyEpochProgress).replace(currentEpochProgress)
              .focus(_.amountToReward).replace(toTokenAmountFormat(0))
              .focus(_.dailyPostsNumber).replace(0)
              .focus(_.videos).replace(List.empty)
              .focus(_.rewardCandidates).modify(_.map(_.filterNot(_.checkUntil.exists(_.value.value <= currentEpochProgress.value.value))))

            buildRewardInfo(currentEpochProgress, youTubeUpdate, searchTerm, firstRewardOfTheDay)
          } else if (isStillAllowedToReward(youTubeUpdate, searchInformation, youtubeDatasource, searchTerm, currentRewardInfo)) {
            buildRewardInfo(currentEpochProgress, youTubeUpdate, searchTerm, currentRewardInfo)
          } else if (youTubeUpdate.video.views >= searchTerm.minimumViews) {
              val updatedVideo = youTubeUpdate.copy(video = youTubeUpdate.video.copy(checkUntil = None))
              currentRewardInfo.focus(_.rewardCandidates).modify(_.map(_.filterNot(_.id == updatedVideo.video.id) :+ updatedVideo.video))
          } else {
            currentRewardInfo
          }

          rewardInfo.focus(_.epochProgressToReward).replace(currentEpochProgress)
        }.getOrElse(buildFirstRewardInfo(currentEpochProgress, youTubeUpdate, searchTerm))

        val updatedDataSourceAddress = youTubeDataSourceAddress
          .focus(_.addressRewards)
          .modify(_.updated(youTubeUpdate.searchText.toLowerCase, updatedRewardInfo))

        youtubeDatasource
          .focus(_.existingWallets)
          .modify(_.updated(youTubeUpdate.address, updatedDataSourceAddress))
      }
  }

  private def isStillAllowedToReward(
    youTubeUpdate: YouTubeUpdate,
    searchInformation: List[ApplicationConfig.YouTubeSearchInfo],
    youtubeDatasource: YouTubeDataSource,
    searchTerm: ApplicationConfig.YouTubeSearchInfo,
    currentRewardInfo: YouTubeRewardInfo
  ): Boolean = {
    isNotYetRewarded(searchInformation, youTubeUpdate, youtubeDatasource) && currentRewardInfo.dailyPostsNumber < searchTerm.maxPerDay
  }

  private def isRewardInfoExpired(
    currentRewardInfo: YouTubeRewardInfo
  ): Boolean =
    currentRewardInfo.dailyEpochProgress.value.value + epochProgressOneDay < currentRewardInfo.epochProgressToReward.value.value

  private def isNotYetRewarded(
    searchInformation: List[SearchInfo],
    youTubeUpdate: YouTubeUpdate,
    youtubeDatasource: YouTubeDataSource
  ): Boolean = {
    youtubeDatasource.existingWallets.get(youTubeUpdate.address).fold(true) { wallet =>
      !searchInformation.exists { searchTerm =>
        wallet.addressRewards.get(searchTerm.text.toLowerCase).exists(_.videos.map(_.id).contains(youTubeUpdate.video.id))
      }
    }
  }

  private def buildRewardInfo(
    currentEpochProgress: EpochProgress,
    youTubeUpdate: YouTubeUpdate,
    searchTerm: ApplicationConfig.YouTubeSearchInfo,
    currentRewardInfo: YouTubeRewardInfo
  ): YouTubeRewardInfo = {
    if (youTubeUpdate.video.views >= searchTerm.minimumViews) {
      val rewardInfoWithUpdatedAmount = if (currentEpochProgress === currentRewardInfo.epochProgressToReward) {
        currentRewardInfo
          .focus(_.amountToReward).modify(current => current.plus(toTokenAmountFormat(searchTerm.rewardAmount)).getOrElse(current))
      } else currentRewardInfo

      rewardInfoWithUpdatedAmount
        .focus(_.dailyPostsNumber).modify(_ + 1)
        .focus(_.videos).modify(_ :+ youTubeUpdate.video)
        .focus(_.rewardCandidates).modify(_.map(_.filterNot(_.id == youTubeUpdate.video.id)))
    } else if (!currentRewardInfo.rewardCandidates.exists(_.map(_.id).contains(youTubeUpdate.video.id))) {
      val updatedVideo = youTubeUpdate.video.copy(checkUntil = Some(getExpirationEpoch(searchTerm, currentRewardInfo)))

      currentRewardInfo
        .focus(_.rewardCandidates).modify(_.map(_.filterNot(_.id == youTubeUpdate.video.id) :+ updatedVideo))
    } else currentRewardInfo
  }

  private def getExpirationEpoch(
    searchInformation: ApplicationConfig.YouTubeSearchInfo,
    currentRewardInfo: RewardInfo
  ): EpochProgress =
    EpochProgress(
      NonNegLong
        .from(currentRewardInfo.dailyEpochProgress.value.value + searchInformation.daysToMonitorVideoUpdates.toDays * epochProgressOneDay.toLong)
        .getOrElse(NonNegLong(0L))
    )

  private def buildFirstRewardInfo(
    currentEpochProgress: EpochProgress,
    youTubeUpdate: YouTubeUpdate,
    searchInformation: ApplicationConfig.YouTubeSearchInfo
  ): YouTubeRewardInfo =
    YouTubeRewardInfo(
      dailyEpochProgress = currentEpochProgress,
      epochProgressToReward = currentEpochProgress,
      amountToReward = toTokenAmountFormat(searchInformation.rewardAmount),
      searchText = searchInformation.text.toLowerCase,
      dailyPostsNumber = 1,
      videos = List(youTubeUpdate.video),
      rewardCandidates = Some(List.empty)
    )

  private def getYouTubeDatasource(
    currentCalculatedState: Map[DataSourceType, DataSource]
  ): YouTubeDataSource =
    currentCalculatedState
      .get(DataSourceType.YouTube)
      .collect { case ds: YouTubeDataSource => ds }
      .getOrElse(YouTubeDataSource(Map.empty))
}
