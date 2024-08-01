package org.elpaca_metagraph.shared_data.daemons.fetcher

import cats.effect.{Async, Resource}
import cats.syntax.all._
import fs2.io.net.Network
import io.circe.generic.auto._
import org.elpaca_metagraph.shared_data.app.ApplicationConfig
import org.elpaca_metagraph.shared_data.types.DataUpdates.{ElpacaUpdate, SimplexUpdate}
import org.elpaca_metagraph.shared_data.types.Simplex.{SimplexApiResponse, SimplexEvent}
import org.http4s._
import org.http4s.circe._
import org.http4s.client.Client
import org.tessellation.node.shared.resources.MkHttpClient
import org.typelevel.ci.CIString
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.time.LocalDateTime
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

      override def getAddressesAndBuildUpdates(currentDate: LocalDateTime): F[List[ElpacaUpdate]] = {
        val simplexConfig = applicationConfig.simplexDaemon
        val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val currentDateFormatted: String = currentDate.format(dateFormatter)
        val url = s"${simplexConfig.apiUrl.get}/proof-of-attendance-metagraph/simplex-events?eventDate=$currentDateFormatted"

        for {
          _ <- logger.info(s"Incoming datetime: ${currentDate}. Formatted to date: ${currentDateFormatted}")
          _ <- logger.info(s"Fetching from Simplex using URL: $url")
          simplexApiResponse <- fetchTransactions(url).handleErrorWith { err =>
            logger.error(s"Error when fetching from Lattice Simplex API: ${err.getMessage}")
              .as(SimplexApiResponse(List.empty[SimplexEvent]))
          }
          _ <- logger.info(s"Found ${simplexApiResponse.data.length} DAG transactions")
          eventsGroupedByAddress = simplexApiResponse.data.groupBy(event => event.destinationWalletAddress)
          dataUpdates = eventsGroupedByAddress.foldLeft(List.empty[ElpacaUpdate]) { (acc, transaction) =>
            val (address, events) = transaction
            acc :+ SimplexUpdate(address, events.toSet)
          }
        } yield dataUpdates
      }

    }
}
