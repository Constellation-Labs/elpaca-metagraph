package org.elpaca_metagraph.shared_data.types


import cats.Eq
import cats.syntax.all._
import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import io.circe.generic.auto._
import org.tessellation.schema.address.Address
import org.tessellation.schema.balance.Amount
import org.tessellation.schema.epoch.EpochProgress

object Simplex {
  case class Payment(
    id            : String,
    status        : String,
    createdAt     : String,
    cryptoCurrency: String,
  )

  @derive(encoder, decoder)
  case class SimplexDataSourceAddress(
    epochProgressToReward: EpochProgress,
    amountToReward       : Amount,
    latestTransactionsIds: Set[String],
    olderTransactionsIds : Set[String]
  )

  case class SimplexEvent(
    eventId                 : String,
    name                    : String,
    blockchainTxnHash       : String,
    destinationWalletAddress: Address,
    payment                 : Payment,
  ) {
    implicit val eqInstance: Eq[SimplexEvent] = Eq.instance { (a, b) =>
      a.eventId === b.eventId
    }
  }

  case class SimplexApiResponse(data: List[SimplexEvent])
}
