package org.proof_of_attendance_metagraph.shared_data.types

import io.circe.generic.auto._
import org.tessellation.schema.address.Address

object IntegrationnetOperatorsTypes {
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
