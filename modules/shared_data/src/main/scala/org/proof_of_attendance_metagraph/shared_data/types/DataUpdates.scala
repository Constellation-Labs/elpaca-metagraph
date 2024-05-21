package org.proof_of_attendance_metagraph.shared_data.types

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import io.circe.generic.auto._
import org.proof_of_attendance_metagraph.shared_data.types.ExolixTypes.ExolixTransaction
import org.proof_of_attendance_metagraph.shared_data.types.IntegrationnetOperatorsTypes.OperatorInQueue
import org.proof_of_attendance_metagraph.shared_data.types.SimplexTypes.SimplexEvent
import org.tessellation.currency.dataApplication.DataUpdate
import org.tessellation.schema.address.Address

object DataUpdates {
  @derive(encoder, decoder)
  sealed trait ProofOfAttendanceUpdate extends DataUpdate {
    val address: Address
  }

  @derive(encoder, decoder)
  case class ExolixUpdate(
    address           : Address,
    exolixTransactions: Set[ExolixTransaction]
  ) extends ProofOfAttendanceUpdate

  @derive(encoder, decoder)
  case class SimplexUpdate(
    address      : Address,
    simplexEvents: Set[SimplexEvent]
  ) extends ProofOfAttendanceUpdate

  @derive(encoder, decoder)
  case class TwitterUpdate(
    address: Address,
  ) extends ProofOfAttendanceUpdate

  @derive(encoder, decoder)
  case class IntegrationnetNodeOperatorUpdate(
    address        : Address,
    operatorInQueue: OperatorInQueue
  ) extends ProofOfAttendanceUpdate
}
