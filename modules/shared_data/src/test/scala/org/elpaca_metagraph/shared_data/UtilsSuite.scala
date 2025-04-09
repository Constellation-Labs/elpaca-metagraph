package org.elpaca_metagraph.shared_data

import eu.timepit.refined.types.numeric.NonNegLong
import org.elpaca_metagraph.shared_data.Utils.isWithinDailyLimit
import org.elpaca_metagraph.shared_data.app.ApplicationConfig.SearchInfo
import org.elpaca_metagraph.shared_data.types.Lattice.{RewardInfo, SocialDataSourceAddress}
import org.scalatest.funsuite.AnyFunSuite
import io.constellationnetwork.schema.balance.Amount
import io.constellationnetwork.schema.epoch.EpochProgress

import scala.collection.immutable.ListMap

class UtilsSuite extends AnyFunSuite {

  case class MockRewardInfo(
    searchText           : String,
    dailyPostsNumber     : Long,
    dailyEpochProgress   : EpochProgress = EpochProgress(NonNegLong(20)),
    epochProgressToReward: EpochProgress = EpochProgress(NonNegLong(20)),
    amountToReward       : Amount = Amount(NonNegLong(5))
  ) extends RewardInfo

  case class MockSocialDataSourceAddress(
    addressRewards: ListMap[String, RewardInfo] = ListMap.empty
  ) extends SocialDataSourceAddress

  case class MockSearchInfo(
    text        : String,
    maxPerDay   : Long,
    rewardAmount: Amount = Amount(NonNegLong(5))
  ) extends SearchInfo

  test("isWithinDailyLimit returns true when no rewards match search info") {
    val searchInfo = MockSearchInfo(text = "test", maxPerDay = 5)
    val wallet = MockSocialDataSourceAddress()

    assert(isWithinDailyLimit(List(searchInfo), wallet))
  }

  test("isWithinDailyLimit returns true when rewards dailyPostsNumber is below maxPerDay") {
    val rewardInfo = MockRewardInfo(
      searchText = "test",
      dailyPostsNumber = 3
    )
    val searchInfo = MockSearchInfo(text = "test", maxPerDay = 5)
    val wallet = MockSocialDataSourceAddress(ListMap("test" -> rewardInfo))

    assert(isWithinDailyLimit(List(searchInfo), wallet))
  }

  test("isWithinDailyLimit returns false when rewards dailyPostsNumber equals maxPerDay") {
    val rewardInfo = MockRewardInfo(
      searchText = "test",
      dailyPostsNumber = 5
    )
    val searchInfo = MockSearchInfo(text = "test", maxPerDay = 5)
    val wallet = MockSocialDataSourceAddress(ListMap("test" -> rewardInfo))

    assert(!isWithinDailyLimit(List(searchInfo), wallet))
  }

  test("isWithinDailyLimit returns false when rewards dailyPostsNumber exceeds maxPerDay") {
    val rewardInfo = MockRewardInfo(
      searchText = "test",
      dailyPostsNumber = 6
    )
    val searchInfo = MockSearchInfo(text = "test", maxPerDay = 5)
    val wallet = MockSocialDataSourceAddress(ListMap("test" -> rewardInfo))

    assert(!isWithinDailyLimit(List(searchInfo), wallet))
  }

  test("isWithinDailyLimit handles case-insensitive search text") {
    val rewardInfo = MockRewardInfo(
      searchText = "TEST",
      dailyPostsNumber = 3
    )
    val searchInfo = MockSearchInfo(text = "test", maxPerDay = 5)
    val wallet = MockSocialDataSourceAddress(ListMap("test" -> rewardInfo))

    assert(isWithinDailyLimit(List(searchInfo), wallet))
  }

  test("isWithinDailyLimit works with multiple search infos") {
    val rewardInfo1 = MockRewardInfo(
      searchText = "test1",
      dailyPostsNumber = 4
    )
    val rewardInfo2 = MockRewardInfo(
      searchText = "test2",
      dailyPostsNumber = 6
    )

    val searchInfo1 = MockSearchInfo(text = "test1", maxPerDay = 5)
    val searchInfo2 = MockSearchInfo(text = "test2", maxPerDay = 5)

    val wallet = MockSocialDataSourceAddress(
      ListMap("test1" -> rewardInfo1, "test2" -> rewardInfo2)
    )

    assert(isWithinDailyLimit(List(searchInfo1, searchInfo2), wallet))
  }
}
