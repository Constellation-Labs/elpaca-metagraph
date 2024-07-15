package org.elpaca_metagraph.shared_data.types

import cats.Eq
import cats.syntax.all._
import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import org.tessellation.schema.balance.Amount
import org.tessellation.schema.epoch.EpochProgress

object Exolix {
  @derive(encoder, decoder)
  case class ExolixDataSourceAddress(
    epochProgressToReward: EpochProgress,
    amountToReward       : Amount,
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
    implicit val eqInstance: Eq[ExolixTransaction] = Eq.instance { (a, b) =>
      a.id === b.id
    }
  }

  case class ExolixApiResponse(data: List[ExolixTransaction])
}
