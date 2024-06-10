package org.elpaca_metagraph.shared_data.types

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import io.circe.generic.auto._
import org.tessellation.schema.address.Address
import org.tessellation.schema.epoch.EpochProgress

object IntegrationnetOperators {
  @derive(encoder, decoder)
  case class IntegrationnetNodeOperatorDataSourceAddress(
    epochProgressToReward: EpochProgress,
    amountToReward       : Long,
    daysInQueue          : Long
  )

  case class OperatorInQueue(
    applicantHash: String,
    daysInQueue  : Long,
    walletBalance: Long,
    walletAddress: Address,
    joinedQueueAt: String,
  ) {
    override def equals(obj: Any): Boolean = obj match {
      case that: OperatorInQueue => this.applicantHash == that.applicantHash
      case _ => false
    }

    override def hashCode(): Int = applicantHash.hashCode()
  }

  case class IntegrationnetOperatorsApiResponse(data: List[OperatorInQueue])
}
