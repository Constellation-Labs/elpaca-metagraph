package org.elpaca_metagraph.shared_data.types

import cats.Eq
import cats.syntax.all._
import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import io.circe.generic.auto._
import org.tessellation.schema.address.Address
import org.tessellation.schema.balance.Amount
import org.tessellation.schema.epoch.EpochProgress

object IntegrationnetOperators {
  @derive(encoder, decoder)
  case class IntegrationnetNodeOperatorDataSourceAddress(
    epochProgressToReward: EpochProgress,
    amountToReward       : Amount,
    daysInQueue          : Long
  )

  case class OperatorInQueue(
    applicantHash: String,
    daysInQueue  : Long,
    walletBalance: Long,
    walletAddress: Address,
    joinedQueueAt: String,
  ) {
    implicit val eqInstance: Eq[OperatorInQueue] = Eq.instance { (a, b) =>
      a.applicantHash === b.applicantHash
    }
  }

  case class IntegrationnetOperatorsApiResponse(data: List[OperatorInQueue])
}
