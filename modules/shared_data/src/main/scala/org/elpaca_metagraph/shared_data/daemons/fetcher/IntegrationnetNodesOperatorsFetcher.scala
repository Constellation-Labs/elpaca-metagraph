package org.elpaca_metagraph.shared_data.daemons.fetcher

import cats.effect.{Async, Resource}
import cats.syntax.all._
import fs2.io.net.Network
import io.circe.generic.auto._
import org.elpaca_metagraph.shared_data.app.ApplicationConfig
import org.elpaca_metagraph.shared_data.types.DataUpdates.{ElpacaUpdate, IntegrationnetNodeOperatorUpdate}
import org.elpaca_metagraph.shared_data.types.IntegrationnetOperators.{IntegrationnetOperatorsApiResponse, OperatorInQueue}
import org.http4s._
import org.http4s.circe._
import org.http4s.client.Client
import org.tessellation.node.shared.resources.MkHttpClient
import org.typelevel.ci.CIString
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.time.LocalDateTime

object IntegrationnetNodesOperatorsFetcher {

  def make[F[_] : Async : Network](applicationConfig: ApplicationConfig): Fetcher[F] =
    new Fetcher[F] {
      private val logger: SelfAwareStructuredLogger[F] = Slf4jLogger.getLoggerFromClass(IntegrationnetNodesOperatorsFetcher.getClass)

      def fetchOperatorsInQueue(url: String): F[IntegrationnetOperatorsApiResponse] = {
        val integrationnetOperatorsConfig = applicationConfig.integrationnetNodesOperatorsDaemon
        val authorizationHeader = CIString("Authorization")

        val clientResource: Resource[F, Client[F]] = MkHttpClient.forAsync[F].newEmber(applicationConfig.http4s.client)

        clientResource.use { client =>
          val request = Request[F](
            method = Method.GET,
            uri = Uri.unsafeFromString(url)
          ).withHeaders(Header.Raw(authorizationHeader, s"ApiKey ${integrationnetOperatorsConfig.apiKey.get}"))

          client.expect[IntegrationnetOperatorsApiResponse](request)(jsonOf[F, IntegrationnetOperatorsApiResponse])
        }
      }

      override def getAddressesAndBuildUpdates(currentDate: LocalDateTime): F[List[ElpacaUpdate]] = {
        val integrationnetOperatorsConfig = applicationConfig.integrationnetNodesOperatorsDaemon
        val url = s"${integrationnetOperatorsConfig.apiUrl.get}/proof-of-attendance-metagraph/integrationnet-nodes-in-queue"

        for {
          _ <- logger.info(s"Fetching from Integrationnet nodes in queue using URL: $url")
          integrationnetOperatorsApiResponse <- fetchOperatorsInQueue(url).handleErrorWith { err =>
            logger.error(s"Error when fetching from Lattice Integrationnet operators API: ${err.getMessage}")
              .as(IntegrationnetOperatorsApiResponse(List.empty[OperatorInQueue]))
          }
          _ <- logger.info(s"Found ${integrationnetOperatorsApiResponse.data.length} operators in queue")
          dataUpdates = integrationnetOperatorsApiResponse.data.foldLeft(List.empty[ElpacaUpdate]) { (acc, operatorInQueue) =>
            acc :+ IntegrationnetNodeOperatorUpdate(operatorInQueue.walletAddress, operatorInQueue)
          }
        } yield dataUpdates
      }
    }
}
