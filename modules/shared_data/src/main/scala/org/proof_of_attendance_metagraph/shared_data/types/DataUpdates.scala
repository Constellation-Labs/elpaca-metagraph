package org.proof_of_attendance_metagraph.shared_data.types

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import org.tessellation.currency.dataApplication.DataUpdate
import org.tessellation.schema.address.Address

object DataUpdates {
  @derive(encoder, decoder)
  sealed trait ProofOfAttendanceUpdate extends DataUpdate {
    val address: Address
  }

  case class ExolixUpdate(
    address: Address,
  ) extends ProofOfAttendanceUpdate

  case class SimplexUpdate(
    address: Address,
  ) extends ProofOfAttendanceUpdate

  case class TwitterUpdate(
    address: Address,
  ) extends ProofOfAttendanceUpdate

  case class IntegrationnetNodeOperatorUpdate(
    address: Address,
  ) extends ProofOfAttendanceUpdate
}
