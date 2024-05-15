package org.proof_of_attendance_metagraph.data_l1.daemons

import cats.effect.Async
import cats.syntax.flatMap._
import cats.syntax.foldable._
import cats.syntax.functor._
import org.proof_of_attendance_metagraph.data_l1.daemons.fetcher.Fetcher
import org.proof_of_attendance_metagraph.shared_data.types.DataUpdates.ProofOfAttendanceUpdate
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

trait Processor[F[_]] {
  def execute: F[Unit]
}

object Processor {

  def make[F[_] : Async](
    fetcher: Fetcher[F],
    signer : Signer[F]
  ): Processor[F] =
    new Processor[F] {

      private val logger: SelfAwareStructuredLogger[F] = Slf4jLogger.getLoggerFromClass(Processor.getClass)

      def execute: F[Unit] =
        process
          .flatMap { remotePending =>
            logger.info(
              s"The daemon attempted processing ${remotePending._1.length + remotePending._2.length} updates from remote. " +
                s"Successful - ${remotePending._1}; Failed - ${remotePending._2}"
            )
          }

      private def process: F[(List[ProofOfAttendanceUpdate], List[ProofOfAttendanceUpdate])] = for {
        updates <- fetcher.getAddressesAndBuildUpdates
        (successUpdated, failureUpdates) = (List.empty[ProofOfAttendanceUpdate], List.empty[ProofOfAttendanceUpdate])
        result <- updates.foldM((successUpdated, failureUpdates)) { (acc, update) =>
          signer.signAndPublish(update).map { success =>
            if (success) (acc._1 :+ update, acc._2)
            else (acc._1, acc._2 :+ update)
          }
        }
      } yield result
    }
}
