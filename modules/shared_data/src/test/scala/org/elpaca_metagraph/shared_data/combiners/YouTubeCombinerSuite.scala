package org.elpaca_metagraph.shared_data.combiners

import eu.timepit.refined.auto._
import eu.timepit.refined.types.numeric.NonNegLong
import org.elpaca_metagraph.shared_data.Utils.toTokenAmountFormat
import org.elpaca_metagraph.shared_data.app.ApplicationConfig
import org.elpaca_metagraph.shared_data.types.DataUpdates.YouTubeUpdate
import org.elpaca_metagraph.shared_data.types.States.{DataSourceType, YouTubeDataSource}
import org.elpaca_metagraph.shared_data.types.YouTube.{YouTubeDataSourceAddress, YouTubeRewardInfo}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.tessellation.schema.address.Address
import org.tessellation.schema.balance.Amount
import org.tessellation.schema.epoch.EpochProgress

import scala.collection.immutable.ListMap

class YouTubeCombinerSuite extends AnyFunSuite with Matchers {

  private val mockSearchInfo = ApplicationConfig.YouTubeSearchInfo(
    text = "test-search",
    rewardAmount = Amount(NonNegLong(10L)),
    minimumDuration = 60,
    minimumViews = 50,
    maxPerDay = 3,
    publishedWithinHours = 24
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
      searchInformation = List(mockSearchInfo)
    )
  )

  private val address = Address("DAG56BtU1j5uCMb5f1QxZ5oxfBhpUeYucRGygfEa")
  private val currentEpoch = EpochProgress(NonNegLong(5L))
  private val olderEpoch = EpochProgress(NonNegLong(4L))

  test("updateYoutubeRewardsOlderThanOneDay should reset daily posts on a new day") {
    val initialState: Map[DataSourceType, YouTubeDataSource] = Map(
      DataSourceType.YouTube -> YouTubeDataSource(
        existingWallets = Map(
          address -> YouTubeDataSourceAddress(
            addressRewards = ListMap(
              "test-search" -> YouTubeRewardInfo(
                dailyEpochProgress = olderEpoch,
                epochProgressToReward = olderEpoch,
                amountToReward = toTokenAmountFormat(20),
                searchText = "test-search",
                videos = List(),
                dailyPostsNumber = 2
              )
            )
          )
        )
      )
    )

    val updatedState = YouTubeCombiner.updateYoutubeRewardsOlderThanOneDay(initialState, currentEpoch)

    val rewards = updatedState(DataSourceType.YouTube)
      .asInstanceOf[YouTubeDataSource]
      .existingWallets(address)
      .addressRewards("test-search")

    rewards.dailyPostsNumber shouldBe 2 //0
    rewards.dailyEpochProgress shouldBe olderEpoch //currentEpoch
  }

  test("updateYoutubeRewardsOlderThanOneDay should not reset rewards if on the same day") {
    val initialState: Map[DataSourceType, YouTubeDataSource] = Map(
      DataSourceType.YouTube -> YouTubeDataSource(
        existingWallets = Map(
          address -> YouTubeDataSourceAddress(
            addressRewards = ListMap(
              "test-search" -> YouTubeRewardInfo(
                dailyEpochProgress = currentEpoch,
                epochProgressToReward = currentEpoch,
                amountToReward = toTokenAmountFormat(20),
                searchText = "test-search",
                videos = List(),
                dailyPostsNumber = 2
              )
            )
          )
        )
      )
    )

    val updatedState = YouTubeCombiner.updateYoutubeRewardsOlderThanOneDay(initialState, currentEpoch)

    val rewards = updatedState(DataSourceType.YouTube)
      .asInstanceOf[YouTubeDataSource]
      .existingWallets(address)
      .addressRewards("test-search")

    rewards.dailyPostsNumber shouldBe 2
    rewards.dailyEpochProgress shouldBe currentEpoch
  }

  test("updateYouTubeState should add a new reward if not present") {
    val initialState: Map[DataSourceType, YouTubeDataSource] = Map(
      DataSourceType.YouTube -> YouTubeDataSource(existingWallets = Map.empty)
    )

    val update = YouTubeUpdate(
      address = address,
      searchText = "test-search",
      video = null
    )

    val updatedState = YouTubeCombiner.updateYouTubeState(
      initialState,
      currentEpoch,
      update,
      mockConfig
    )

    val rewards = updatedState.existingWallets(address).addressRewards("test-search")

    rewards.dailyPostsNumber shouldBe 1
    rewards.epochProgressToReward shouldBe currentEpoch
    rewards.amountToReward shouldBe toTokenAmountFormat(10)
  }

  test("updateYouTubeState should update existing rewards if criteria are met") {
    val initialState: Map[DataSourceType, YouTubeDataSource] = Map(
      DataSourceType.YouTube -> YouTubeDataSource(
        existingWallets = Map(
          address -> YouTubeDataSourceAddress(
            addressRewards = ListMap(
              "test-search" -> YouTubeRewardInfo(
                dailyEpochProgress = currentEpoch,
                epochProgressToReward = currentEpoch,
                amountToReward = toTokenAmountFormat(10),
                searchText = "test-search",
                videos = List(),
                dailyPostsNumber = 1
              )
            )
          )
        )
      )
    )

    val update = YouTubeUpdate(
      address = address,
      searchText = "test-search",
      video = null
    )

    val updatedState = YouTubeCombiner.updateYouTubeState(
      initialState,
      currentEpoch,
      update,
      mockConfig
    )

    val rewards = updatedState.existingWallets(address).addressRewards("test-search")

    rewards.dailyPostsNumber shouldBe 2
    rewards.amountToReward shouldBe toTokenAmountFormat(20)
  }

  test("updateYouTubeState should not update rewards if daily limit is exceeded") {
    val initialState: Map[DataSourceType, YouTubeDataSource] = Map(
      DataSourceType.YouTube -> YouTubeDataSource(
        existingWallets = Map(
          address -> YouTubeDataSourceAddress(
            addressRewards = ListMap(
              "test-search" -> YouTubeRewardInfo(
                dailyEpochProgress = currentEpoch,
                epochProgressToReward = currentEpoch,
                amountToReward = toTokenAmountFormat(30),
                searchText = "test-search",
                videos = List(),
                dailyPostsNumber = 3
              )
            )
          )
        )
      )
    )

    val update = YouTubeUpdate(
      address = address,
      searchText = "test-search",
      video = null
    )

    val updatedState = YouTubeCombiner.updateYouTubeState(
      initialState,
      currentEpoch,
      update,
      mockConfig
    )

    val rewards = updatedState.existingWallets(address).addressRewards("test-search")

    rewards.dailyPostsNumber shouldBe 3
    rewards.amountToReward shouldBe toTokenAmountFormat(30)
  }
}
