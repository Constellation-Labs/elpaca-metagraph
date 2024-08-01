package org.elpaca_metagraph.shared_data.types

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import org.tessellation.schema.address.Address
import org.tessellation.schema.balance.Amount
import org.tessellation.schema.epoch.EpochProgress

object X {
  @derive(encoder, decoder)
  case class XRewardInfo(
    dailyEpochProgress   : EpochProgress,
    epochProgressToReward: EpochProgress,
    amountToReward       : Amount,
    searchText           : String,
    postIds              : List[String],
    dailyPostsNumber     : Long,
  )


  @derive(encoder, decoder)
  case class XDataSourceAddress(
    addressRewards: Map[String, XRewardInfo]
  )

  object XDataSourceAddress {
    def empty: XDataSourceAddress = XDataSourceAddress(Map.empty)
  }

  @derive(encoder, decoder)
  case class XUser(
    id      : String,
    name    : String,
    username: String
  )

  @derive(encoder, decoder)
  case class SourceUser(
    id               : String,
    primaryDagAddress: Option[Address],
    twitter          : Option[XUser]
  )

  @derive(encoder, decoder)
  case class SourceUserMeta(
    total : Long,
    limit : Long,
    offset: Long
  )

  @derive(encoder, decoder)
  case class SourceUsersApiResponse(
    data: List[SourceUser],
    meta: SourceUserMeta
  )

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
