package org.proof_of_attendance_metagraph.data_l1.daemons.fetcher

import cats.effect.{Async, Resource}
import cats.syntax.applicativeError._
import cats.syntax.flatMap._
import cats.syntax.functor._
import eu.timepit.refined.api.RefType.applyRef
import fs2.io.net.Network
import io.circe.generic.auto._
import org.http4s._
import org.http4s.circe._
import org.http4s.client.Client
import org.proof_of_attendance_metagraph.shared_data.app.ApplicationConfig
import org.proof_of_attendance_metagraph.shared_data.types.DataUpdates.{ExolixUpdate, ProofOfAttendanceUpdate}
import org.proof_of_attendance_metagraph.shared_data.types.ExolixTypes.{ExolixApiResponse, ExolixTransaction}
import org.tessellation.node.shared.resources.MkHttpClient
import org.tessellation.schema.address.{Address, DAGAddress}
import org.typelevel.ci.CIString
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.time.LocalDate
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

      override def getAddressesAndBuildUpdates: F[List[ProofOfAttendanceUpdate]] = {
        val exolixConfig = applicationConfig.exolixDaemon
        val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val currentDate: String = LocalDate.now().format(dateFormatter)
        val url = s"${exolixConfig.apiUrl.get}/transactions?dateFrom=${currentDate}T00:00:00&dateTo=${currentDate}T23:59:59"

        for {
          _ <- logger.info(s"Fetching from Exolix using URL: $url")
          exolixApiResponse <- fetchTransactions(url).handleErrorWith { err =>
            logger.error(s"Error when fetching from exolix API: ${err.getMessage}")
              .as(ExolixApiResponse(List.empty[ExolixTransaction]))
          }
          _ <- logger.info(s"Found ${exolixApiResponse.data.length} transactions")
          transactionsToDAG = exolixApiResponse.data.filter(transaction => transaction.coinTo.coinCode == "DAG")
          _ <- logger.info(s"Found ${transactionsToDAG.length} to DAG token transactions")
          dataUpdates = transactionsToDAG.foldLeft(List.empty[ProofOfAttendanceUpdate]) { (acc, transaction) =>
            applyRef[DAGAddress](transaction.withdrawalAddress) match {
              case Left(_) => acc
              case Right(dagAddress) => acc :+ ExolixUpdate(Address(dagAddress), transaction)
            }
          }
          _ <- logger.info(s"Exolix Updates: $dataUpdates")
        } yield dataUpdates
      }
    }
}
