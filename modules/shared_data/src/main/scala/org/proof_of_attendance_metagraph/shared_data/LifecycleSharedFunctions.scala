package org.proof_of_attendance_metagraph.shared_data

import cats.effect.Async
import cats.syntax.all._
import org.proof_of_attendance_metagraph.shared_data.combiners.ProofOfAttendanceCombiner.combineProofOfAttendance
import org.proof_of_attendance_metagraph.shared_data.types.DataUpdates.ProofOfAttendanceUpdate
import org.proof_of_attendance_metagraph.shared_data.types.States.{ProofOfAttendanceCalculatedState, ProofOfAttendanceOnChainState}
import org.tessellation.currency.dataApplication.{DataState, L0NodeContext}
import org.tessellation.schema.epoch.EpochProgress
import org.tessellation.security.signature.Signed
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

object LifecycleSharedFunctions {
  def logger[F[_] : Async]: SelfAwareStructuredLogger[F] = Slf4jLogger.getLoggerFromName[F]("LifecycleSharedFunctions")

  def combine[F[_] : Async](
    oldState: DataState[ProofOfAttendanceOnChainState, ProofOfAttendanceCalculatedState],
    updates : List[Signed[ProofOfAttendanceUpdate]]
  )(implicit context: L0NodeContext[F]): F[DataState[ProofOfAttendanceOnChainState, ProofOfAttendanceCalculatedState]] = {
    val newState = DataState(ProofOfAttendanceOnChainState(List.empty), ProofOfAttendanceCalculatedState(oldState.calculated.addresses))

    if (updates.isEmpty) {
      logger.info("Snapshot without any check-ins, updating the state to empty updates").as(newState)
    } else {
      updates.foldLeftM(newState) { (acc, signedUpdate) =>
        for {
          epochProgress <- context.getLastCurrencySnapshot.flatMap {
            case Some(value) => value.epochProgress.pure[F]
            case None =>
              val message = "Could not get the epochProgress from currency snapshot. lastCurrencySnapshot not found"
              logger.error(message) >> new Exception(message).raiseError[F, EpochProgress]
          }
          _ <- logger.info(s"New checkIn for the device: $signedUpdate")
        } yield combineProofOfAttendance(
          acc,
          epochProgress,
          signedUpdate
        )
      }
    }
  }
}