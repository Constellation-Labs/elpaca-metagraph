package org.proof_of_attendance_metagraph.shared_data.calculated_state

import org.proof_of_attendance_metagraph.shared_data.types.States.ProofOfAttendanceCalculatedState
import org.tessellation.schema.SnapshotOrdinal

case class CalculatedState(ordinal: SnapshotOrdinal, state: ProofOfAttendanceCalculatedState)

object CalculatedState {
  def empty: CalculatedState =
    CalculatedState(SnapshotOrdinal.MinValue, ProofOfAttendanceCalculatedState(Map.empty))
}
