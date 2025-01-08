package org.elpaca_metagraph.shared_data.types

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import org.elpaca_metagraph.shared_data.types.Lattice._
import org.tessellation.schema.address.Address
import org.tessellation.schema.balance.Amount
import org.tessellation.schema.epoch.EpochProgress

import scala.collection.immutable.ListMap

object X {
  @derive(encoder, decoder)
  case class XRewardInfo(
    dailyEpochProgress   : EpochProgress,
    epochProgressToReward: EpochProgress,
    amountToReward       : Amount,
    searchText           : String,
    postIds              : List[String],
    dailyPostsNumber     : Long
  ) extends RewardInfo

  @derive(encoder, decoder)
  case class XDataSourceAddress(
    addressRewards: ListMap[String, XRewardInfo]
  ) extends SocialDataSourceAddress

  object XDataSourceAddress {
    def empty: XDataSourceAddress = XDataSourceAddress(ListMap.empty)
  }

  @derive(encoder, decoder)
  case class NoteTweet(
    text: String
  )

  @derive(encoder, decoder)
  case class XPost(
    id        : String,
    text      : String,
    note_tweet: Option[NoteTweet]
  )

  @derive(encoder, decoder)
  case class XApiResponseMetadata(
    result_count: Long
  )

  @derive(encoder, decoder)
  case class XApiResponse(
    data: Option[List[XPost]],
    meta: XApiResponseMetadata
  )

  @derive(encoder, decoder)
  case class XDataInfo(
    postId               : String,
    dagAddress           : Address,
    searchText           : String,
    limitSearchTextPerDay: Long
  )
}
