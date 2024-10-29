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
  def updateYouTubeState(
    currentCalculatedState: Map[DataSourceType, DataSource],
    currentEpochProgress: EpochProgress,
    youTubeUpdate: YouTubeUpdate,
    applicationConfig: ApplicationConfig
  ): YouTubeDataSource = {
    val searchInformation = applicationConfig.youtubeDaemon.searchInformation
    val amountToReward = toTokenAmountFormat(searchInformation.rewardAmount)
    val youtubeDatasource = currentCalculatedState
      .get(DataSourceType.YouTube)
      .collect { case ds: YouTubeDataSource => ds }
      .getOrElse(YouTubeDataSource(Map.empty))

    val record = youtubeDatasource.existingWallets.getOrElse(youTubeUpdate.address, YouTubeDataSourceAddress())

    val updatedData = record.videoRewards.get(youTubeUpdate.videoId).map { data =>
      def isNotExceedingDailyLimit = record.rewardsReceivedToday <= searchInformation.maxPerDay

      def updateYouTubeRewardInfoNewDay(): YouTubeRewardInfo = {
        data
          .focus(_.dailyEpochProgress)
          .replace(currentEpochProgress)
          .focus(_.epochProgressToReward)
          .replace(currentEpochProgress)
          .focus(_.amountToReward)
          .replace(amountToReward)
          .focus(_.publishDate)
          .replace(youTubeUpdate.publishDate)
      }

      def updateYouTubeRewardInfoSameDay(): YouTubeRewardInfo = {
        if (data.epochProgressToReward === currentEpochProgress) {
          data
            .focus(_.amountToReward)
            .modify(current => current.plus(amountToReward).getOrElse(current))
            .focus(_.publishDate)
            .replace(youTubeUpdate.publishDate)
        } else {
          data
            .focus(_.epochProgressToReward)
            .replace(currentEpochProgress)
            .focus(_.publishDate)
            .replace(youTubeUpdate.publishDate)
        }
      }

      if (isNewDay(data.epochProgressToReward, currentEpochProgress)) updateYouTubeRewardInfoNewDay()
      else if (isNotExceedingDailyLimit) updateYouTubeRewardInfoSameDay()
      else data
    }.getOrElse(YouTubeRewardInfo(currentEpochProgress, currentEpochProgress, amountToReward, youTubeUpdate.publishDate))

    val updatedDataSourceAddress = if (isNewDay(updatedData.epochProgressToReward, currentEpochProgress)) {
      record
        .focus(_.rewardsReceivedToday)
        .replace(1)
        .focus(_.videoRewards)
        .modify(_.updated(youTubeUpdate.videoId, updatedData))
    } else {
      record
        .focus(_.rewardsReceivedToday)
        .modify(_ + 1)
        .focus(_.videoRewards)
        .modify(_.updated(youTubeUpdate.videoId, updatedData))
    }

    youtubeDatasource
      .focus(_.existingWallets)
      .modify(_.updated(youTubeUpdate.address, updatedDataSourceAddress))
  }
}
