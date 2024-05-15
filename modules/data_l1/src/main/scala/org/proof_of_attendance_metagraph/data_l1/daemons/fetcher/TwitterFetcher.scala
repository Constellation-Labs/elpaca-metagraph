package org.proof_of_attendance_metagraph.data_l1.daemons.fetcher

import cats.effect.Async
import cats.syntax.flatMap._
import cats.syntax.applicative._
import eu.timepit.refined.auto._
import org.proof_of_attendance_metagraph.shared_data.app.ApplicationConfig.TwitterDaemonConfig
import org.proof_of_attendance_metagraph.shared_data.types.DataUpdates.{ProofOfAttendanceUpdate, TwitterUpdate}
import org.tessellation.schema.address.Address
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

object TwitterFetcher {

  def make[F[_] : Async](twitterDaemonConfig: TwitterDaemonConfig): Fetcher[F] =
    new Fetcher[F] {
      private val logger: SelfAwareStructuredLogger[F] = Slf4jLogger.getLoggerFromClass(TwitterFetcher.getClass)

      override def getAddressesAndBuildUpdates: F[List[ProofOfAttendanceUpdate]] =
        logger.info("Fetching from TwitterFetcher") >>
          List[ProofOfAttendanceUpdate](
            TwitterUpdate(Address("DAG0KpQNqMsED4FC5grhFCBWG8iwU8Gm6aLhB9w5"))
          ).pure

    }
}
