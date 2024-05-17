package org.proof_of_attendance_metagraph.data_l1.daemons.fetcher

import cats.effect.{Async, Resource}
import cats.syntax.applicativeError._
import cats.syntax.flatMap._
import cats.syntax.functor._
import fs2.io.net.Network
import io.circe.generic.auto._
import org.http4s._
import org.http4s.circe._
import org.http4s.client.Client
import org.proof_of_attendance_metagraph.shared_data.app.ApplicationConfig
import org.proof_of_attendance_metagraph.shared_data.types.DataUpdates.{ProofOfAttendanceUpdate, SimplexUpdate}
import org.proof_of_attendance_metagraph.shared_data.types.SimplexTypes.{SimplexApiResponse, SimplexEvent}
import org.tessellation.node.shared.resources.MkHttpClient
import org.typelevel.ci.CIString
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.time.LocalDate
import java.time.format.DateTimeFormatter

object SimplexFetcher {

  def make[F[_] : Async : Network](applicationConfig: ApplicationConfig): Fetcher[F] =
    new Fetcher[F] {
      private val logger: SelfAwareStructuredLogger[F] = Slf4jLogger.getLoggerFromClass(SimplexFetcher.getClass)

      def fetchTransactions(url: String): F[SimplexApiResponse] = {
        val simplexConfig = applicationConfig.simplexDaemon
        val authorizationHeader = CIString("Authorization")

        val clientResource: Resource[F, Client[F]] = MkHttpClient.forAsync[F].newEmber(applicationConfig.http4s.client)

        clientResource.use { client =>
          val request = Request[F](
            method = Method.GET,
            uri = Uri.unsafeFromString(url)
          ).withHeaders(Header.Raw(authorizationHeader, s"ApiKey ${simplexConfig.apiKey.get}"))

          client.expect[SimplexApiResponse](request)(jsonOf[F, SimplexApiResponse])
        }
      }

      override def getAddressesAndBuildUpdates: F[List[ProofOfAttendanceUpdate]] = {
        val simplexConfig = applicationConfig.simplexDaemon
        val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val currentDate: String = LocalDate.now().format(dateFormatter)
        val url = s"${simplexConfig.apiUrl.get}/proof-of-attendance-metagraph/simplex-events?eventDate=${currentDate}"

        for {
          _ <- logger.info(s"Fetching from Simplex using URL: $url")
          simplexApiResponse <- fetchTransactions(url).handleErrorWith { err =>
            logger.error(s"Error when fetching from Lattice Simplex API: ${err.getMessage}")
              .as(SimplexApiResponse(List.empty[SimplexEvent]))
          }
          _ <- logger.info(s"Found ${simplexApiResponse.data.length} DAG transactions")
          eventsGroupedByAddress = simplexApiResponse.data.groupBy(event => event.destinationWalletAddress)
          dataUpdates = eventsGroupedByAddress.foldLeft(List.empty[ProofOfAttendanceUpdate]) { (acc, transaction) =>
            val (address, events) = transaction
            acc :+ SimplexUpdate(address, events.toSet)
          }

          _ <- logger.info(s"Simplex Updates: $dataUpdates")
        } yield dataUpdates
      }
    }
}
