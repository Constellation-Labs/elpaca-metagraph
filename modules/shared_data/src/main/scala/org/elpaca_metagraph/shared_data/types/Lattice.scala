package org.elpaca_metagraph.shared_data.types

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import org.tessellation.schema.address.Address
import org.tessellation.schema.balance.Amount
import org.tessellation.schema.epoch.EpochProgress

import scala.collection.immutable.ListMap

object Lattice {
  trait RewardInfo {
    def dailyEpochProgress   : EpochProgress
    def epochProgressToReward: EpochProgress
    def amountToReward       : Amount
    def searchText           : String
    def dailyPostsNumber     : Long
  }

  trait SocialDataSourceAddress {
    def addressRewards: ListMap[String, RewardInfo]
  }

  @derive(encoder, decoder)
  case class YouTubeAccount(
    channelId: String
  )

  @derive(encoder, decoder)
  case class XAccount(
    username: String
  )

  @derive(encoder, decoder)
  case class LatticeUser(
    id               : String,
    primaryDagAddress: Option[Address],
    linkedAccounts   : Option[LinkedAccounts],
    twitter          : Option[XAccount],
  )

  @derive(encoder, decoder)
  case class LinkedAccounts(
    youtube: Option[YouTubeAccount],
    twitter: Option[XAccount]
  )

  @derive(encoder, decoder)
  case class LatticeUserMeta(
    total : Long,
    limit : Long,
    offset: Long
  )

  @derive(encoder, decoder)
  case class LatticeUsersApiResponse(
    data: List[LatticeUser],
    meta: Option[LatticeUserMeta]
  )
}
