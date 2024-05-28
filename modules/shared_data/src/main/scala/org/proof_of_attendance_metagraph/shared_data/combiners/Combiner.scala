package org.proof_of_attendance_metagraph.shared_data.combiners

import cats.effect.Async
import cats.syntax.functor._
import org.proof_of_attendance_metagraph.shared_data.combiners.ExolixCombiner.updateStateExolixResponse
import org.proof_of_attendance_metagraph.shared_data.combiners.IntegrationnetOperatorsCombiner.updateStateIntegrationnetOperatorsResponse
import org.proof_of_attendance_metagraph.shared_data.combiners.SimplexCombiner.updateStateSimplexResponse
import org.proof_of_attendance_metagraph.shared_data.combiners.WalletCreationCombiner.updateStateWalletCreation
import org.proof_of_attendance_metagraph.shared_data.types.DataUpdates._
import org.proof_of_attendance_metagraph.shared_data.types.States._
import org.tessellation.currency.dataApplication.DataState
import org.tessellation.schema.epoch.EpochProgress
import org.tessellation.security.signature.Signed
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

object Combiner {
  def combineProofOfAttendance[F[_] : Async](
    oldState            : DataState[ProofOfAttendanceOnChainState, ProofOfAttendanceCalculatedState],
    currentEpochProgress: EpochProgress,
    update              : Signed[ProofOfAttendanceUpdate]
  ): F[DataState[ProofOfAttendanceOnChainState, ProofOfAttendanceCalculatedState]] = {
    val updatedCalculatedStateF = update.value match {
      case update: ExolixUpdate =>
        implicit val logger = Slf4jLogger.getLoggerFromName[F]("ExolixCombiner")
        updateStateExolixResponse(
          oldState.calculated.dataSources,
          currentEpochProgress,
          update
        )
      case update: SimplexUpdate =>
        implicit val logger: SelfAwareStructuredLogger[F] = Slf4jLogger.getLoggerFromName[F]("SimplexCombiner")
        updateStateSimplexResponse(
          oldState.calculated.dataSources,
          currentEpochProgress,
          update
        )

      case update: IntegrationnetNodeOperatorUpdate =>
        implicit val logger: SelfAwareStructuredLogger[F] = Slf4jLogger.getLoggerFromName[F]("IntegrationnetOperatorsCombiner")
        updateStateIntegrationnetOperatorsResponse(
          oldState.calculated.dataSources,
          currentEpochProgress,
          update
        )

      case update: WalletCreationUpdate =>
        implicit val logger: SelfAwareStructuredLogger[F] = Slf4jLogger.getLoggerFromName[F]("WalletCreationCombiner")
        updateStateWalletCreation(
          oldState.calculated.dataSources,
          currentEpochProgress,
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