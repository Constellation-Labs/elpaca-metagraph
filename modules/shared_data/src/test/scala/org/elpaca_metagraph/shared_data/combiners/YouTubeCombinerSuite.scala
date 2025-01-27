package org.elpaca_metagraph.shared_data.combiners

import cats.syntax.all._
import eu.timepit.refined.auto._
import eu.timepit.refined.types.numeric.NonNegLong
import org.elpaca_metagraph.shared_data.Utils.toTokenAmountFormat
import org.elpaca_metagraph.shared_data.app.ApplicationConfig
import org.elpaca_metagraph.shared_data.types.DataUpdates.YouTubeUpdate
import org.elpaca_metagraph.shared_data.types.States.{DataSourceType, YouTubeDataSource}
import org.elpaca_metagraph.shared_data.types.YouTube.YouTubeDataAPI.VideoDetails
import org.elpaca_metagraph.shared_data.types.YouTube.{YouTubeDataSourceAddress, YouTubeRewardInfo}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.tessellation.schema.address.Address
import org.tessellation.schema.balance.Amount
import org.tessellation.schema.epoch.EpochProgress

import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.collection.immutable.ListMap
import scala.concurrent.duration._

class YouTubeCombinerSuite extends AnyFunSuite with Matchers {

  private val mockSearchInfo = ApplicationConfig.YouTubeSearchInfo(
    text = "dag",
    rewardAmount = Amount(NonNegLong(50L)),
    minimumDuration = 60.seconds,
    minimumViews = 50,
    maxPerDay = 1,
    publishedWithinHours = 3.hours,
    daysToMonitorVideoUpdates = 30.days
  )

  private val mockSearchInfo2 = ApplicationConfig.YouTubeSearchInfo(
    text = "americasblockchain",
    rewardAmount = Amount(NonNegLong(50L)),
    minimumDuration = 60.seconds,
    minimumViews = 50,
    maxPerDay = 1,
    publishedWithinHours = 3.hours,
    daysToMonitorVideoUpdates = 30.days
  )

  private val mockConfig = ApplicationConfig(
    http4s = null,
    dataApi = null,
    exolixDaemon = null,
    simplexDaemon = null,
    integrationnetNodesOperatorsDaemon = null,
    walletCreationHoldingDagDaemon = null,
    inflowTransactionsDaemon = null,
    outflowTransactionsDaemon = null,
    nodeKey = null,
    xDaemon = null,
    streak = null,
    youtubeDaemon = ApplicationConfig.YouTubeDaemonConfig(
      idleTime = scala.concurrent.duration.Duration.Zero,
      usersSourceApiUrl = None,
      youtubeApiUrl = None,
      youtubeApiKey = None,
      searchInformation = List(mockSearchInfo, mockSearchInfo2)
    )
  )

  private val address = Address("DAG56BtU1j5uCMb5f1QxZ5oxfBhpUeYucRGygfEa")
  private val currentEpoch = EpochProgress(NonNegLong(5L))
  private val previousEpoch = EpochProgress(NonNegLong(4L))

  test("first video from a search term should be moved to candidate - not enough views") {
    val initialState: Map[DataSourceType, YouTubeDataSource] = Map(
      DataSourceType.YouTube -> YouTubeDataSource(
        existingWallets = Map.empty
      )
    )

    val mockedUpdate = YouTubeUpdate(
      address,
      "dag",
      VideoDetails(
        "TOpRlODvi44",
        "UCI1DMmfinDIQdfSlVwvI1Uw",
        Instant.now(),
        35,
        254,
        None
      ))

    val updatedState = YouTubeCombiner.updateYouTubeState(initialState, currentEpoch, mockedUpdate, mockConfig)

    val rewards = updatedState
      .existingWallets(address)
      .addressRewards("dag")

    rewards.dailyPostsNumber shouldBe 0
    rewards.dailyEpochProgress shouldBe EpochProgress.MinValue
    rewards.epochProgressToReward shouldBe EpochProgress.MinValue
    rewards.amountToReward shouldBe Amount(NonNegLong.MinValue)
    rewards.rewardCandidates.get.length shouldBe 1
  }

  test("too short videos should be discarded - first video") {
    val initialState: Map[DataSourceType, YouTubeDataSource] = Map(
      DataSourceType.YouTube -> YouTubeDataSource(
        existingWallets = Map.empty
      )
    )

    val mockedUpdate = YouTubeUpdate(
      address,
      "dag",
      VideoDetails(
        "TOpRlODvi44",
        "UCI1DMmfinDIQdfSlVwvI1Uw",
        Instant.now(),
        100,
        10,
        None
      ))

    val updatedState = YouTubeCombiner.updateYouTubeState(initialState, currentEpoch, mockedUpdate, mockConfig)

    val rewards = updatedState
      .existingWallets(address)
      .addressRewards("dag")

    rewards.dailyPostsNumber shouldBe 0
    rewards.dailyEpochProgress shouldBe EpochProgress.MinValue
    rewards.epochProgressToReward shouldBe EpochProgress.MinValue
    rewards.amountToReward shouldBe Amount(NonNegLong.MinValue)
    rewards.rewardCandidates shouldBe None
  }

  test("too short videos should be discarded - next videos") {
    val initialState: Map[DataSourceType, YouTubeDataSource] = Map(
      DataSourceType.YouTube -> YouTubeDataSource(
        existingWallets = Map(
          address -> YouTubeDataSourceAddress(
            addressRewards = ListMap(
              "dag" -> YouTubeRewardInfo(
                dailyEpochProgress = EpochProgress.MinValue,
                epochProgressToReward = EpochProgress.MinValue,
                amountToReward = Amount.empty,
                searchText = "dag",
                rewardedVideos = List(),
                dailyPostsNumber = 0,
                rewardCandidates = None
              )
            )
          )
        )
      )
    )

    val mockedUpdate = YouTubeUpdate(
      address,
      "dag",
      VideoDetails(
        "TOpRlODvi44",
        "UCI1DMmfinDIQdfSlVwvI1Uw",
        Instant.now(),
        100,
        10,
        None
      ))

    val updatedState = YouTubeCombiner.updateYouTubeState(initialState, currentEpoch, mockedUpdate, mockConfig)

    val rewards = updatedState
      .existingWallets(address)
      .addressRewards("dag")

    rewards.dailyPostsNumber shouldBe 0
    rewards.dailyEpochProgress shouldBe EpochProgress.MinValue
    rewards.epochProgressToReward shouldBe EpochProgress.MinValue
    rewards.amountToReward shouldBe Amount(NonNegLong.MinValue)
    rewards.rewardCandidates shouldBe None
  }

  test("candidate should be moved to rewarded once video have the requirements") {
    val initialState: Map[DataSourceType, YouTubeDataSource] = Map(
      DataSourceType.YouTube -> YouTubeDataSource(
        existingWallets = Map(
          address -> YouTubeDataSourceAddress(
            addressRewards = ListMap(
              "dag" -> YouTubeRewardInfo(
                dailyEpochProgress = EpochProgress.MinValue,
                epochProgressToReward = EpochProgress.MinValue,
                amountToReward = Amount.empty,
                searchText = "dag",
                rewardedVideos = List(),
                dailyPostsNumber = 0,
                rewardCandidates = List(VideoDetails(
                  "TOpRlODvi44",
                  "UCI1DMmfinDIQdfSlVwvI1Uw",
                  Instant.now(),
                  10,
                  10,
                  None
                )).some
              )
            )
          )
        )
      )
    )

    val mockedUpdate = YouTubeUpdate(
      address,
      "dag",
      VideoDetails(
        "TOpRlODvi44",
        "UCI1DMmfinDIQdfSlVwvI1Uw",
        Instant.now(),
        1000,
        1000,
        None
      ))

    val updatedState = YouTubeCombiner.updateYouTubeState(initialState, currentEpoch, mockedUpdate, mockConfig)

    val rewards = updatedState
      .existingWallets(address)
      .addressRewards("dag")

    rewards.dailyPostsNumber shouldBe 1
    rewards.dailyEpochProgress shouldBe currentEpoch
    rewards.epochProgressToReward shouldBe currentEpoch
    rewards.amountToReward shouldBe toTokenAmountFormat(50)
    rewards.rewardCandidates.get.length shouldBe 0
    rewards.rewardedVideos.length shouldBe 1
  }

  test("should clean rewarded videos after 30 days") {
    val initialState: Map[DataSourceType, YouTubeDataSource] = Map(
      DataSourceType.YouTube -> YouTubeDataSource(
        existingWallets = Map(
          address -> YouTubeDataSourceAddress(
            addressRewards = ListMap(
              "dag" -> YouTubeRewardInfo(
                dailyEpochProgress = EpochProgress.MinValue,
                epochProgressToReward = EpochProgress.MinValue,
                amountToReward = Amount.empty,
                searchText = "dag",
                rewardedVideos = List(VideoDetails(
                  "TOpRlODvi44",
                  "UCI1DMmfinDIQdfSlVwvI1Uw",
                  Instant.now().minus(40, ChronoUnit.DAYS),
                  10,
                  10,
                  None
                )),
                dailyPostsNumber = 0,
                rewardCandidates = Some(List.empty)
              )
            )
          )
        )
      )
    )

    val updatedState = YouTubeCombiner.cleanYoutubeDataSource(initialState, currentEpoch)

    val rewards = updatedState(DataSourceType.YouTube)
      .asInstanceOf[YouTubeDataSource]
      .existingWallets(address)
      .addressRewards("dag")

    rewards.rewardCandidates.get.length shouldBe 0
    rewards.rewardedVideos.length shouldBe 0
  }

  test("should clean expired candidates") {
    val initialState: Map[DataSourceType, YouTubeDataSource] = Map(
      DataSourceType.YouTube -> YouTubeDataSource(
        existingWallets = Map(
          address -> YouTubeDataSourceAddress(
            addressRewards = ListMap(
              "dag" -> YouTubeRewardInfo(
                dailyEpochProgress = EpochProgress.MinValue,
                epochProgressToReward = EpochProgress.MinValue,
                amountToReward = Amount.empty,
                searchText = "dag",
                rewardedVideos = List.empty,
                dailyPostsNumber = 0,
                rewardCandidates = Some(List(VideoDetails(
                  "TOpRlODvi44",
                  "UCI1DMmfinDIQdfSlVwvI1Uw",
                  Instant.now().minus(40, ChronoUnit.DAYS),
                  10,
                  10,
                  EpochProgress.MinValue.some
                )))
              )
            )
          )
        )
      )
    )

    val updatedState = YouTubeCombiner.cleanYoutubeDataSource(initialState, currentEpoch)

    val rewards = updatedState(DataSourceType.YouTube)
      .asInstanceOf[YouTubeDataSource]
      .existingWallets(address)
      .addressRewards("dag")

    rewards.rewardCandidates.get.length shouldBe 0
    rewards.rewardedVideos.length shouldBe 0
  }

  test("should not reward twice the same video") {
    val initialState: Map[DataSourceType, YouTubeDataSource] = Map(
      DataSourceType.YouTube -> YouTubeDataSource(
        existingWallets = Map(
          address -> YouTubeDataSourceAddress(
            addressRewards = ListMap(
              "dag" -> YouTubeRewardInfo(
                dailyEpochProgress = EpochProgress.MinValue,
                epochProgressToReward = EpochProgress.MinValue,
                amountToReward = Amount.empty,
                searchText = "dag",
                rewardedVideos = List(VideoDetails(
                  "TOpRlODvi44",
                  "UCI1DMmfinDIQdfSlVwvI1Uw",
                  Instant.now(),
                  1000,
                  1000,
                  None
                )),
                dailyPostsNumber = 0,
                rewardCandidates = Some(List.empty)
              )
            )
          )
        )
      )
    )

    val mockedUpdate = YouTubeUpdate(
      address,
      "dag",
      VideoDetails(
        "TOpRlODvi44",
        "UCI1DMmfinDIQdfSlVwvI1Uw",
        Instant.now(),
        1000,
        1000,
        None
      ))

    val updatedState = YouTubeCombiner.updateYouTubeState(initialState, currentEpoch, mockedUpdate, mockConfig)

    val rewards = updatedState
      .existingWallets(address)
      .addressRewards("dag")

    rewards.dailyPostsNumber shouldBe 0
    rewards.dailyEpochProgress shouldBe EpochProgress.MinValue
    rewards.epochProgressToReward shouldBe EpochProgress.MinValue
    rewards.amountToReward shouldBe Amount.empty
    rewards.rewardCandidates.get.length shouldBe 0
    rewards.rewardedVideos.length shouldBe 1
  }

  test("updateYoutubeRewardsOlderThanOneDay should reset daily posts on a new day") {
    val initialState: Map[DataSourceType, YouTubeDataSource] = Map(
      DataSourceType.YouTube -> YouTubeDataSource(
        existingWallets = Map(
          address -> YouTubeDataSourceAddress(
            addressRewards = ListMap(
              "dag" -> YouTubeRewardInfo(
                dailyEpochProgress = previousEpoch,
                epochProgressToReward = previousEpoch,
                amountToReward = toTokenAmountFormat(50),
                searchText = "dag",
                rewardedVideos = List(),
                dailyPostsNumber = 0
              )
            )
          )
        )
      )
    )

    val updatedState = YouTubeCombiner.cleanYoutubeDataSource(initialState, currentEpoch)

    val rewards = updatedState(DataSourceType.YouTube)
      .asInstanceOf[YouTubeDataSource]
      .existingWallets(address)
      .addressRewards("dag")

    rewards.dailyPostsNumber shouldBe 0
    rewards.dailyEpochProgress shouldBe previousEpoch
  }

  test("updateYoutubeRewardsOlderThanOneDay should not reset rewards if on the same day") {
    val initialState: Map[DataSourceType, YouTubeDataSource] = Map(
      DataSourceType.YouTube -> YouTubeDataSource(
        existingWallets = Map(
          address -> YouTubeDataSourceAddress(
            addressRewards = ListMap(
              "dag" -> YouTubeRewardInfo(
                dailyEpochProgress = currentEpoch,
                epochProgressToReward = currentEpoch,
                amountToReward = toTokenAmountFormat(20),
                searchText = "dag",
                rewardedVideos = List(),
                dailyPostsNumber = 2
              )
            )
          )
        )
      )
    )

    val updatedState = YouTubeCombiner.cleanYoutubeDataSource(initialState, currentEpoch)

    val rewards = updatedState(DataSourceType.YouTube)
      .asInstanceOf[YouTubeDataSource]
      .existingWallets(address)
      .addressRewards("dag")

    rewards.dailyPostsNumber shouldBe 2
    rewards.dailyEpochProgress shouldBe currentEpoch
  }

  test("updateYouTubeState should add a new reward if not present") {
    val initialState: Map[DataSourceType, YouTubeDataSource] = Map(
      DataSourceType.YouTube -> YouTubeDataSource(existingWallets = Map.empty)
    )

    val update = YouTubeUpdate(
      address = address,
      searchText = "dag",
      video = VideoDetails(
        "TOpRlODvi44",
        "UCI1DMmfinDIQdfSlVwvI1Uw",
        Instant.now(),
        1000,
        1000,
        None
      )
    )

    val updatedState = YouTubeCombiner.updateYouTubeState(
      initialState,
      currentEpoch,
      update,
      mockConfig
    )

    val rewards = updatedState.existingWallets(address).addressRewards("dag")

    rewards.dailyPostsNumber shouldBe 1
    rewards.epochProgressToReward shouldBe currentEpoch
    rewards.amountToReward shouldBe toTokenAmountFormat(50)
  }

  test("updateYouTubeState should update existing rewards if criteria are met") {
    val initialState: Map[DataSourceType, YouTubeDataSource] = Map(
      DataSourceType.YouTube -> YouTubeDataSource(
        existingWallets = Map(
          address -> YouTubeDataSourceAddress(
            addressRewards = ListMap(
              "dag" -> YouTubeRewardInfo(
                dailyEpochProgress = currentEpoch,
                epochProgressToReward = currentEpoch,
                amountToReward = toTokenAmountFormat(50),
                searchText = "dag",
                rewardedVideos = List(),
                dailyPostsNumber = 1
              )
            )
          )
        )
      )
    )

    val update = YouTubeUpdate(
      address = address,
      searchText = "dag",
      video = VideoDetails(
        id = "test-id",
        channelId = "channel-1",
        publishedAt = Instant.now(),
        views = 100,
        duration = 180
      )
    )

    val updatedState = YouTubeCombiner.updateYouTubeState(
      initialState,
      currentEpoch,
      update,
      mockConfig
    )

    val rewards = updatedState.existingWallets(address).addressRewards("dag")

    rewards.dailyPostsNumber shouldBe 1
    rewards.amountToReward shouldBe toTokenAmountFormat(50)
  }

  test("updateYouTubeState should not update rewards if daily limit is exceeded") {
    val initialState: Map[DataSourceType, YouTubeDataSource] = Map(
      DataSourceType.YouTube -> YouTubeDataSource(
        existingWallets = Map(
          address -> YouTubeDataSourceAddress(
            addressRewards = ListMap(
              "dag" -> YouTubeRewardInfo(
                dailyEpochProgress = currentEpoch,
                epochProgressToReward = currentEpoch,
                amountToReward = toTokenAmountFormat(30),
                searchText = "dag",
                rewardedVideos = List(),
                dailyPostsNumber = 3
              )
            )
          )
        )
      )
    )

    val update = YouTubeUpdate(
      address = address,
      searchText = "dag",
      video = VideoDetails(
        id = "test-id",
        channelId = "channel-1",
        publishedAt = Instant.now(),
        views = 100,
        duration = 180
      )
    )

    val updatedState = YouTubeCombiner.updateYouTubeState(
      initialState,
      currentEpoch,
      update,
      mockConfig
    )

    val rewards = updatedState.existingWallets(address).addressRewards("dag")

    rewards.dailyPostsNumber shouldBe 3
    rewards.amountToReward shouldBe toTokenAmountFormat(30)
  }

  test("updateYouTubeState should try to reward if views reaches criteria after second trial") {
    val initialState: Map[DataSourceType, YouTubeDataSource] = Map(
      DataSourceType.YouTube -> YouTubeDataSource(
        existingWallets = Map(
          address -> YouTubeDataSourceAddress(
            addressRewards = ListMap(
              "dag" -> YouTubeRewardInfo(
                dailyEpochProgress = currentEpoch,
                epochProgressToReward = currentEpoch,
                amountToReward = toTokenAmountFormat(0),
                searchText = "dag",
                dailyPostsNumber = 0,
                rewardedVideos = List()
              )
            )
          )
        )
      )
    )

    val update = YouTubeUpdate(
      address = address,
      searchText = "dag",
      video = VideoDetails(
        id = "test-id",
        channelId = "channel-1",
        publishedAt = Instant.now(),
        views = 45,
        duration = 180
      )
    )

    val updatedState = YouTubeCombiner.updateYouTubeState(
      initialState,
      currentEpoch,
      update,
      mockConfig
    )

    val rewards = updatedState.existingWallets(address).addressRewards("dag")

    rewards.dailyPostsNumber shouldBe 0
    rewards.amountToReward shouldBe toTokenAmountFormat(0)
    // Once the update video doesn't meet criteria this time, it shall be stacked in rewardCandidates
    rewards.rewardCandidates.get.head.id shouldBe "test-id"

    // Same video sent on retrial
    val newUpdatedState = YouTubeCombiner.updateYouTubeState(
      Map(DataSourceType.YouTube -> updatedState),
      EpochProgress(NonNegLong(5)),
      update.copy(video = update.video.copy(views = 100)), // Now, video reached the required views criteria and should be rewarded
      mockConfig
    )

    val updatedRewards = newUpdatedState.existingWallets(address).addressRewards("dag")

    updatedRewards.dailyPostsNumber shouldBe 1
    updatedRewards.amountToReward shouldBe toTokenAmountFormat(50)
    // As the video is rewarded, it should be removed from rewardCandidates
    updatedRewards.rewardCandidates.get.size shouldBe 0
  }
}
