package org.elpaca_metagraph.shared_data.daemons.fetcher

import cats.effect.{Async, Resource}
import cats.syntax.all._
import eu.timepit.refined.api.RefType.applyRef
import fs2.io.net.Network
import io.circe.generic.auto._
import org.elpaca_metagraph.shared_data.app.ApplicationConfig
import org.elpaca_metagraph.shared_data.types.DataUpdates.{ElpacaUpdate, ExolixUpdate}
import org.elpaca_metagraph.shared_data.types.Exolix.{ExolixApiResponse, ExolixTransaction}
import org.http4s._
import org.http4s.circe._
import org.http4s.client.Client
import org.tessellation.node.shared.resources.MkHttpClient
import org.tessellation.schema.address.{Address, DAGAddress}
import org.typelevel.ci.CIString
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object ExolixFetcher {

  def make[F[_] : Async : Network](applicationConfig: ApplicationConfig): Fetcher[F] =
    new Fetcher[F] {
      private val logger: SelfAwareStructuredLogger[F] = Slf4jLogger.getLoggerFromClass(ExolixFetcher.getClass)

      def fetchTransactions(url: String): F[ExolixApiResponse] = {
        val exolixConfig = applicationConfig.exolixDaemon
        val authorizationHeader = CIString("Authorization")

        val clientResource: Resource[F, Client[F]] = MkHttpClient.forAsync[F].newEmber(applicationConfig.http4s.client)

        clientResource.use { client =>
          val request = Request[F](
            method = Method.GET,
            uri = Uri.unsafeFromString(url)
          ).withHeaders(Header.Raw(authorizationHeader, s"Bearer ${exolixConfig.apiKey.get}"))

          client.expect[ExolixApiResponse](request)(jsonOf[F, ExolixApiResponse])
        }
      }

      override def getAddressesAndBuildUpdates(currentDate: LocalDateTime): F[List[ElpacaUpdate]] = {
        val exolixConfig = applicationConfig.exolixDaemon
        val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val currentDateFormatted: String = currentDate.format(dateFormatter)
        val url = s"${exolixConfig.apiUrl.get}/transactions?dateFrom=${currentDateFormatted}T00:00:00&dateTo=${currentDateFormatted}T23:59:59&statuses=success"

        for {
          _ <- logger.info(s"Incoming datetime: ${currentDate}. Formatted to date: ${currentDateFormatted}")
          _ <- logger.info(s"Fetching from Exolix using URL: $url")
          exolixApiResponse <- fetchTransactions(url).handleErrorWith { err =>
            logger.error(s"Error when fetching from exolix API: ${err.getMessage}")
              .as(ExolixApiResponse(List.empty[ExolixTransaction]))
          }
          _ <- logger.info(s"Found ${exolixApiResponse.data.length} transactions")
          transactionsToDAG = exolixApiResponse.data.filter(transaction => transaction.coinTo.coinCode == "DAG")
          _ <- logger.info(s"Found ${transactionsToDAG.length} to DAG token transactions")
          _ <- logger.info(s"To DAG transactions: ${transactionsToDAG}")

          transactionsGroupedByAddress = transactionsToDAG.groupBy(event => event.withdrawalAddress)
          dataUpdates = transactionsGroupedByAddress.foldLeft(List.empty[ElpacaUpdate]) { (acc, info) =>
            val (address, transactions) = info
            applyRef[DAGAddress](address) match {
              case Left(_) => acc
              case Right(dagAddress) => acc :+ ExolixUpdate(Address(dagAddress), transactions.toSet)
            }
          }
        } yield dataUpdates
      }
    }
}
