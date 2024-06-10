package org.elpaca_metagraph.shared_data.types

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import org.tessellation.schema.epoch.EpochProgress

object Exolix {
  @derive(encoder, decoder)
  case class ExolixDataSourceAddress(
    epochProgressToReward: EpochProgress,
    amountToReward       : Long,
    latestTransactionsIds: Set[String],
    olderTransactionsIds : Set[String]
  )

  case class CoinInfo(
    coinCode: String,
    coinName: String,
  )

  case class ExolixTransaction(
    id               : String,
    amount           : Double,
    coinFrom         : CoinInfo,
    coinTo           : CoinInfo,
    status           : String,
    createdAt        : String,
    depositAddress   : String,
    withdrawalAddress: String
  ) {
    override def equals(obj: Any): Boolean = obj match {
      case that: ExolixTransaction => this.id == that.id
      case _ => false
    }

    override def hashCode(): Int = id.hashCode()
  }

  case class ExolixApiResponse(data: List[ExolixTransaction])
}
