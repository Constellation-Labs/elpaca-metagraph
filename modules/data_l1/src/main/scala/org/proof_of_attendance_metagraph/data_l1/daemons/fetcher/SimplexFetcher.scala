package org.proof_of_attendance_metagraph.data_l1.daemons.fetcher

import cats.effect.Async
import cats.implicits.{catsSyntaxApplicativeId, catsSyntaxFlatMapOps}
import eu.timepit.refined.auto._
import org.proof_of_attendance_metagraph.shared_data.app.ApplicationConfig.SimplexDaemonConfig
import org.proof_of_attendance_metagraph.shared_data.types.DataUpdates.{ProofOfAttendanceUpdate, SimplexUpdate}
import org.tessellation.schema.address.Address
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

object SimplexFetcher {

  def make[F[_] : Async](simplexDaemonConfig: SimplexDaemonConfig): Fetcher[F] =
    new Fetcher[F] {
      private val logger: SelfAwareStructuredLogger[F] = Slf4jLogger.getLoggerFromClass(SimplexFetcher.getClass)

      override def getAddressesAndBuildUpdates: F[List[ProofOfAttendanceUpdate]] =
        logger.info("Fetching from SimplexFetcher") >>
          List[ProofOfAttendanceUpdate](
            SimplexUpdate(Address("DAG0KpQNqMsED4FC5grhFCBWG8iwU8Gm6aLhB9w5"))
          ).pure

    }
}
