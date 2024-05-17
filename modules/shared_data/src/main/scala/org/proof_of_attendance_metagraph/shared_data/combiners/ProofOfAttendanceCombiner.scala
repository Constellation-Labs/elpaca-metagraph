package org.proof_of_attendance_metagraph.shared_data.combiners

import cats.effect.Async
import cats.syntax.functor._
import org.proof_of_attendance_metagraph.shared_data.combiners.ExolixCombiner.updateStateExolixResponse
import org.proof_of_attendance_metagraph.shared_data.combiners.IntegrationnetOperatorsCombiner.updateStateIntegrationnetOperatorsResponse
import org.proof_of_attendance_metagraph.shared_data.combiners.SimplexCombiner.updateStateSimplexResponse
import org.proof_of_attendance_metagraph.shared_data.combiners.TwitterCombiner.updateStateTwitterResponse
import org.proof_of_attendance_metagraph.shared_data.types.DataUpdates._
import org.proof_of_attendance_metagraph.shared_data.types.States._
import org.tessellation.currency.dataApplication.DataState
import org.tessellation.ext.cats.syntax.next.catsSyntaxNext
import org.tessellation.schema.epoch.EpochProgress
import org.tessellation.security.signature.Signed

object ProofOfAttendanceCombiner {
  val epoch_progress_1_day: Long = 1440L

  def combineProofOfAttendance[F[_] : Async](
    oldState            : DataState[ProofOfAttendanceOnChainState, ProofOfAttendanceCalculatedState],
    currentEpochProgress: EpochProgress,
    update              : Signed[ProofOfAttendanceUpdate]
  ): F[DataState[ProofOfAttendanceOnChainState, ProofOfAttendanceCalculatedState]] = {
    val updatedCalculatedStateF = update.value match {
      case update: ExolixUpdate =>
        updateStateExolixResponse(
          oldState.calculated.addresses,
          currentEpochProgress.next,
          update
        )
      case update: SimplexUpdate =>
        updateStateSimplexResponse(
          oldState.calculated.addresses,
          currentEpochProgress.next,
          update
        )
      case update: TwitterUpdate =>
        updateStateTwitterResponse(
          oldState.calculated.addresses,
          currentEpochProgress.next,
          update
        )
      case update: IntegrationnetNodeOperatorUpdate =>
        updateStateIntegrationnetOperatorsResponse(
          oldState.calculated.addresses,
          currentEpochProgress.next,
          update
        )
    }

    val updates: List[ProofOfAttendanceUpdate] = update.value :: oldState.onChain.updates

    updatedCalculatedStateF.map(updatedCalculatedState => DataState(
      ProofOfAttendanceOnChainState(updates),
      ProofOfAttendanceCalculatedState(updatedCalculatedState)
    ))
  }
}