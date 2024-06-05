package org.elpaca_metagraph.shared_data.types

import io.circe.generic.auto._
import org.tessellation.schema.address.Address

object SimplexTypes {
  case class Payment(
    id            : String,
    status        : String,
    createdAt     : String,
    cryptoCurrency: String,
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
