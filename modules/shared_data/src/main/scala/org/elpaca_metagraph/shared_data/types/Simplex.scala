package org.elpaca_metagraph.shared_data.types

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import io.circe.generic.auto._
import org.tessellation.schema.address.Address
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
    amountToReward       : Long,
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
    override def equals(obj: Any): Boolean = obj match {
      case that: SimplexEvent => this.eventId == that.eventId
      case _ => false
    }

    override def hashCode(): Int = eventId.hashCode()
  }

  case class SimplexApiResponse(data: List[SimplexEvent])
}
