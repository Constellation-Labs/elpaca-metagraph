package org.proof_of_attendance_metagraph.shared_data.daemons.fetcher

import org.proof_of_attendance_metagraph.shared_data.types.DataUpdates.ProofOfAttendanceUpdate

trait Fetcher[F[_]] {
  def getAddressesAndBuildUpdates: F[List[ProofOfAttendanceUpdate]]
}
