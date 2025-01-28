package org.elpaca_metagraph.shared_data.combiners

import cats.syntax.all._
import eu.timepit.refined.types.numeric.NonNegLong
import monocle.Monocle.toAppliedFocusOps
import org.elpaca_metagraph.shared_data.Utils._
import org.elpaca_metagraph.shared_data.app.ApplicationConfig
import org.elpaca_metagraph.shared_data.types.DataUpdates.YouTubeUpdate
import org.elpaca_metagraph.shared_data.types.States.{DataSource, DataSourceType, YouTubeDataSource}
import org.elpaca_metagraph.shared_data.types.YouTube.{YouTubeDataSourceAddress, YouTubeRewardInfo}
import org.tessellation.schema.epoch.EpochProgress

import java.time.Instant
import java.time.temporal.ChronoUnit

object YouTubeCombiner {
  private val firstVideoOfTheDay: Long = 1L

  def cleanYoutubeDataSource(
    currentCalculatedState: Map[DataSourceType, DataSource],
    currentEpochProgress  : EpochProgress
  ): Map[DataSourceType, DataSource] = {
    val youtubeDatasource = getYouTubeDatasource(currentCalculatedState)
    val updatedDataSourceAddress = youtubeDatasource.existingWallets
      .foldLeft(youtubeDatasource.existingWallets) { (acc, current) =>
        val (address, youtubeDataSourceAddress) = current
        val updatedRewards = youtubeDataSourceAddress
          .addressRewards
          .foldLeft(youtubeDataSourceAddress.addressRewards) { (innerAcc, innerCurrent) =>
            val (searchText, rewardInfo) = innerCurrent
            val calculatedStateUpdatedByExpiring = if (isNewDay(rewardInfo.dailyEpochProgress, currentEpochProgress)) {
              innerAcc.updated(searchText, rewardInfo.focus(_.dailyPostsNumber).replace(0))
            } else {
              innerAcc
            }

            calculatedStateUpdatedByExpiring.updated(searchText,
              rewardInfo
                .focus(_.rewardCandidates).modify {
                  case Some(videoDetails) => videoDetails.filter { videoDetail =>
                    videoDetail.checkUntil.exists(_.value.value > currentEpochProgress.value.value)
                  }.some
                  case current@None => current
                }
                .focus(_.videos).modify { rewardedVideos =>
                  rewardedVideos.filter { rewardedVideo =>
                    Instant.now().isBefore(rewardedVideo.publishedAt.plus(30, ChronoUnit.DAYS))
                  }
                }
            )
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
    currentEpochProgress  : EpochProgress,
    youTubeUpdate         : YouTubeUpdate,
    applicationConfig     : ApplicationConfig
  ): YouTubeDataSource = {
    val youtubeDatasource = getYouTubeDatasource(currentCalculatedState)
    val youTubeDataSourceAddress = youtubeDatasource.existingWallets
      .get(youTubeUpdate.address) match {
      case Some(youtubeDataSourceAddress) => youtubeDataSourceAddress
      case None => YouTubeDataSourceAddress.empty
    }

    val youtubeDataSourceAddressExistentVideos = youtubeDatasource.existingWallets
      .get(youTubeUpdate.address) match {
      case Some(youTubeDataSourceAddress) =>
        youTubeDataSourceAddress
          .addressRewards.values
          .flatMap(_.videos.map(_.id))
          .toList
      case None => List.empty[String]
    }

    val youTubeUpdateSearchText = youTubeUpdate.searchText.toLowerCase

    val maybeSearchInformation = applicationConfig.youtubeDaemon.searchInformation
      .find(_.text.toLowerCase == youTubeUpdateSearchText)

    maybeSearchInformation.fold(youtubeDatasource) { searchTerm =>
      val updatedData = youTubeDataSourceAddress.addressRewards
        .get(youTubeUpdateSearchText)
        .map { data =>
          def isNotExceedingDailyLimit = data.dailyPostsNumber < searchTerm.maxPerDay

          def videoAlreadyExists = youtubeDataSourceAddressExistentVideos.contains(youTubeUpdate.video.id)

          def videoAlreadyRewardCandidate = data.rewardCandidates.exists(_.map(_.id).contains(youTubeUpdate.video.id))

          def videoTooShort = youTubeUpdate.video.duration < searchTerm.minimumDuration.toSeconds

          if (isRewardInfoExpired(data)) {
            data
              .focus(_.dailyEpochProgress).replace(data.epochProgressToReward)
              .focus(_.dailyPostsNumber).replace(searchTerm.maxPerDay)
          } else if (videoAlreadyExists || (!videoWithAllRequirements(youTubeUpdate, searchTerm) && videoAlreadyRewardCandidate) || videoTooShort) {
            data
          } else if (!videoWithAllRequirements(youTubeUpdate, searchTerm)) {
            data
              .focus(_.rewardCandidates)
              .modify(rc => Some(rc.getOrElse(List.empty) :+ youTubeUpdate.video.copy(checkUntil = Some(getExpirationEpoch(searchTerm, currentEpochProgress)))))
          } else if (isNewDay(data.epochProgressToReward, currentEpochProgress)) {
            data
              .focus(_.dailyEpochProgress).replace(currentEpochProgress)
              .focus(_.epochProgressToReward).replace(currentEpochProgress)
              .focus(_.amountToReward).replace(toTokenAmountFormat(searchTerm.rewardAmount))
              .focus(_.dailyPostsNumber).replace(firstVideoOfTheDay)
              .focus(_.videos).modify(_ :+ youTubeUpdate.video)
              .focus(_.rewardCandidates).modify(_.getOrElse(List.empty).filter(_.id != youTubeUpdate.video.id).some)
          } else if (isNotExceedingDailyLimit && !videoAlreadyExists) {
            data
              .focus(_.dailyPostsNumber).modify(_ + 1)
              .focus(_.amountToReward).modify(current => current.plus(toTokenAmountFormat(searchTerm.rewardAmount)).getOrElse(current))
              .focus(_.videos).modify(_ :+ youTubeUpdate.video)
              .focus(_.rewardCandidates).modify(_.getOrElse(List.empty).filter(_.id != youTubeUpdate.video.id).some)
          } else {
            data
          }
        }.getOrElse(buildFirstRewardInfo(currentEpochProgress, youTubeUpdate, searchTerm))

      val updatedDataSourceAddress = youTubeDataSourceAddress
        .focus(_.addressRewards)
        .modify(_.updated(youTubeUpdateSearchText, updatedData))

      youtubeDatasource
        .focus(_.existingWallets)
        .modify(_.updated(youTubeUpdate.address, updatedDataSourceAddress))
    }
  }

  private def isRewardInfoExpired(
    currentRewardInfo: YouTubeRewardInfo
  ): Boolean =
    currentRewardInfo.dailyEpochProgress.value.value + epochProgressOneDay < currentRewardInfo.epochProgressToReward.value.value

  private def getExpirationEpoch(
    searchInformation   : ApplicationConfig.YouTubeSearchInfo,
    currentEpochProgress: EpochProgress
  ): EpochProgress =
    EpochProgress(
      NonNegLong
        .from(currentEpochProgress.value.value + searchInformation.daysToMonitorVideoUpdates.toDays * epochProgressOneDay.toLong)
        .getOrElse(NonNegLong(0L))
    )

  private def videoWithAllRequirements(
    youTubeUpdate    : YouTubeUpdate,
    searchInformation: ApplicationConfig.YouTubeSearchInfo
  ): Boolean =
    youTubeUpdate.video.views >= searchInformation.minimumViews &&
      youTubeUpdate.video.duration >= searchInformation.minimumDuration.toSeconds

  private def buildFirstRewardInfo(
    currentEpochProgress: EpochProgress,
    youTubeUpdate       : YouTubeUpdate,
    searchInformation   : ApplicationConfig.YouTubeSearchInfo
  ): YouTubeRewardInfo = {
    def videoTooShort = youTubeUpdate.video.duration < searchInformation.minimumDuration.toSeconds

    if (videoTooShort) {
      YouTubeRewardInfo
        .empty(searchInformation.text)
    } else if (videoWithAllRequirements(youTubeUpdate, searchInformation)) {
      YouTubeRewardInfo(
        dailyEpochProgress = currentEpochProgress,
        epochProgressToReward = currentEpochProgress,
        amountToReward = toTokenAmountFormat(searchInformation.rewardAmount),
        searchText = searchInformation.text.toLowerCase,
        dailyPostsNumber = 1,
        videos = List(youTubeUpdate.video),
        rewardCandidates = Some(List.empty)
      )
    } else {
      YouTubeRewardInfo
        .empty(searchInformation.text)
        .copy(rewardCandidates = Some(
          List(youTubeUpdate.video.copy(checkUntil = Some(getExpirationEpoch(searchInformation, currentEpochProgress)))))
        )
    }
  }

  private def getYouTubeDatasource(
    currentCalculatedState: Map[DataSourceType, DataSource]
  ): YouTubeDataSource =
    currentCalculatedState
      .get(DataSourceType.YouTube)
      .collect { case ds: YouTubeDataSource => ds }
      .getOrElse(YouTubeDataSource(Map.empty))
}
