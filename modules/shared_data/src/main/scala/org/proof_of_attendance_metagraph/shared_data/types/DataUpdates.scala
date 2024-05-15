package org.proof_of_attendance_metagraph.shared_data.types

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import org.proof_of_attendance_metagraph.shared_data.types.ExolixTypes.ExolixTransaction
import org.tessellation.currency.dataApplication.DataUpdate
import org.tessellation.schema.address.Address
import io.circe.generic.auto._

object DataUpdates {
  @derive(encoder, decoder)
  sealed trait ProofOfAttendanceUpdate extends DataUpdate {
    val address: Address
  }

  @derive(encoder, decoder)
  case class ExolixUpdate(
    address: Address,
    exolixTransaction: ExolixTransaction
  ) extends ProofOfAttendanceUpdate

  @derive(encoder, decoder)
  case class SimplexUpdate(
    address: Address,
  ) extends ProofOfAttendanceUpdate

  @derive(encoder, decoder)
  case class TwitterUpdate(
    address: Address,
  ) extends ProofOfAttendanceUpdate

  @derive(encoder, decoder)
  case class IntegrationnetNodeOperatorUpdate(
    address: Address,
  ) extends ProofOfAttendanceUpdate
}
