package org.proof_of_attendance_metagraph.shared_data

import cats.effect.Async
import cats.syntax.all._
import org.proof_of_attendance_metagraph.shared_data.combiners.Combiner.combineProofOfAttendance
import org.proof_of_attendance_metagraph.shared_data.types.DataUpdates.{IntegrationnetNodeOperatorUpdate, ProofOfAttendanceUpdate}
import org.proof_of_attendance_metagraph.shared_data.types.States.{ProofOfAttendanceCalculatedState, ProofOfAttendanceOnChainState}
import org.proof_of_attendance_metagraph.shared_data.validations.Errors.valid
import org.proof_of_attendance_metagraph.shared_data.validations.Validations.integrationnetNodeOperatorsValidationsL1
import org.tessellation.currency.dataApplication.dataApplication.DataApplicationValidationErrorOr
import org.tessellation.currency.dataApplication.{DataState, L0NodeContext}
import org.tessellation.ext.cats.syntax.next.catsSyntaxNext
import org.tessellation.schema.epoch.EpochProgress
import org.tessellation.security.signature.Signed
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

object LifecycleSharedFunctions {
  def logger[F[_] : Async]: SelfAwareStructuredLogger[F] = Slf4jLogger.getLoggerFromName[F]("LifecycleSharedFunctions")

  def validateUpdate[F[_] : Async](
    update: ProofOfAttendanceUpdate
  ): F[DataApplicationValidationErrorOr[Unit]] =
    update match {
      case integrationnetOpUpdate: IntegrationnetNodeOperatorUpdate =>
        Async[F].delay {
          integrationnetNodeOperatorsValidationsL1(integrationnetOpUpdate)
        }
      case _ => valid.pure[F]
    }

  def combine[F[_] : Async](
    oldState: DataState[ProofOfAttendanceOnChainState, ProofOfAttendanceCalculatedState],
    updates : List[Signed[ProofOfAttendanceUpdate]]
  )(implicit context: L0NodeContext[F]): F[DataState[ProofOfAttendanceOnChainState, ProofOfAttendanceCalculatedState]] = {
    val newState = DataState(ProofOfAttendanceOnChainState(List.empty), ProofOfAttendanceCalculatedState(oldState.calculated.dataSources))

    if (updates.isEmpty) {
      logger.info("Snapshot without any updates, updating the state to empty updates").as(newState)
    } else {
      logger.info(s"Incoming updates: ${updates.length}") >>
        updates.foldLeftM(newState) { (acc, signedUpdate) =>
          for {
            epochProgress <- context.getLastCurrencySnapshot.flatMap {
              case Some(value) => value.epochProgress.next.pure[F]
              case None =>
                val message = "Could not get the epochProgress from currency snapshot. lastCurrencySnapshot not found"
                logger.error(message) >> new Exception(message).raiseError[F, EpochProgress]
            }

            updatedState <- combineProofOfAttendance(
              acc,
              epochProgress,
              signedUpdate
            )
          } yield updatedState
        }
    }
  }
}