package org.proof_of_attendance_metagraph.data_l1.daemons.fetcher

import cats.effect.Async
import cats.implicits.{catsSyntaxApplicativeId, catsSyntaxFlatMapOps}
import eu.timepit.refined.auto._
import org.proof_of_attendance_metagraph.shared_data.app.ApplicationConfig.ExolixDaemonConfig
import org.proof_of_attendance_metagraph.shared_data.types.DataUpdates.{ExolixUpdate, ProofOfAttendanceUpdate}
import org.tessellation.schema.address.Address
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

object ExolixFetcher {

  def make[F[_] : Async](exolixDaemonConfig: ExolixDaemonConfig): Fetcher[F] =
    new Fetcher[F] {
      private val logger: SelfAwareStructuredLogger[F] = Slf4jLogger.getLoggerFromClass(ExolixFetcher.getClass)

      override def getAddressesAndBuildUpdates: F[List[ProofOfAttendanceUpdate]] =
        logger.info("Fetching from Exolix") >>
          List[ProofOfAttendanceUpdate](
            ExolixUpdate(Address("DAG0KpQNqMsED4FC5grhFCBWG8iwU8Gm6aLhB9w5"))
          ).pure
    }
}
