package org.elpaca_metagraph.shared_data.combiners

import cats.syntax.all._
import monocle.Monocle.toAppliedFocusOps
import org.elpaca_metagraph.shared_data.Utils.{isNewDay, toTokenAmountFormat}
import org.elpaca_metagraph.shared_data.app.ApplicationConfig
import org.elpaca_metagraph.shared_data.types.DataUpdates.YouTubeUpdate
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
    val maybeSearchInformation = applicationConfig.youtubeDaemon.searchInformation.find(_.text == youTubeUpdate.searchText)
    val youtubeDatasource = getYouTubeDatasource(currentCalculatedState)
    val youTubeDataSourceAddress = youtubeDatasource.existingWallets.getOrElse(youTubeUpdate.address, YouTubeDataSourceAddress())

    maybeSearchInformation.fold(youtubeDatasource) { searchInformation =>
      val updatedData = youTubeDataSourceAddress.addressRewards
        .get(youTubeUpdate.searchText)
        .map { data =>
          def updateYoutubeRewardInfoNewDay() = {
            data
              .focus(_.dailyEpochProgress)
              .replace(currentEpochProgress)
              .focus(_.epochProgressToReward)
              .replace(currentEpochProgress)
              .focus(_.dailyPostsNumber)
              .replace(1)
              .focus(_.amountToReward)
              .replace(toTokenAmountFormat(searchInformation.rewardAmount))
              .focus(_.videos)
              .replace(List(youTubeUpdate.video))
          }

          def isNotExceedingDailyLimit: Boolean = data.dailyPostsNumber < searchInformation.maxPerDay

          def videoAlreadyExists: Boolean = data.videos.contains(youTubeUpdate.video)

          def updateYTRewardInfoSameDay(): YouTubeRewardInfo = {
            if (data.epochProgressToReward === currentEpochProgress) {
              data
                .focus(_.dailyPostsNumber)
                .modify(current => current + 1)
                .focus(_.amountToReward)
                .modify(current => current.plus(toTokenAmountFormat(searchInformation.rewardAmount)).getOrElse(current))
                .focus(_.videos)
                .modify(current => current :+ youTubeUpdate.video)
            } else {
              data
                .focus(_.epochProgressToReward)
                .replace(currentEpochProgress)
                .focus(_.dailyPostsNumber)
                .modify(current => current + 1)
                .focus(_.videos)
                .modify(current => current :+ youTubeUpdate.video)
            }
          }

          if (isNewDay(data.epochProgressToReward, currentEpochProgress)) {
            updateYoutubeRewardInfoNewDay()
          } else if (isNotExceedingDailyLimit && !videoAlreadyExists) {
            updateYTRewardInfoSameDay()
          } else {
            data
          }
        }.getOrElse(YouTubeRewardInfo(
          currentEpochProgress,
          currentEpochProgress,
          toTokenAmountFormat(searchInformation.rewardAmount),
          searchInformation.text,
          List(youTubeUpdate.video),
          1
        ))

      val updatedDataSourceAddress = youTubeDataSourceAddress
        .focus(_.addressRewards)
        .modify(_.updated(youTubeUpdate.searchText, updatedData))

      youtubeDatasource
        .focus(_.existingWallets)
        .modify(_.updated(youTubeUpdate.address, updatedDataSourceAddress))
    }
  }

  private def getYouTubeDatasource(currentCalculatedState: Map[DataSourceType, DataSource]): YouTubeDataSource = {
    currentCalculatedState
      .get(DataSourceType.YouTube)
      .collect { case ds: YouTubeDataSource => ds }
      .getOrElse(YouTubeDataSource(Map.empty))
  }
}
