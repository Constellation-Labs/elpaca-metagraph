package org.proof_of_attendance_metagraph.data_l1.daemons.fetcher

import org.proof_of_attendance_metagraph.shared_data.types.DataUpdates.ProofOfAttendanceUpdate

trait Fetcher[F[_]] {
  def getAddressesAndBuildUpdates: F[List[ProofOfAttendanceUpdate]]
}
