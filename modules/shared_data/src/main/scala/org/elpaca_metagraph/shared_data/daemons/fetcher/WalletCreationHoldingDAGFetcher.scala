package org.elpaca_metagraph.shared_data.daemons.fetcher

import cats.effect.{Async, Resource}
import cats.syntax.all._
import fs2.io.net.Network
import io.circe.generic.auto._
import org.elpaca_metagraph.shared_data.app.ApplicationConfig
import org.elpaca_metagraph.shared_data.calculated_state.CalculatedStateService
import org.elpaca_metagraph.shared_data.types.DataUpdates.{ElpacaUpdate, WalletCreationHoldingDAGUpdate}
import org.elpaca_metagraph.shared_data.types.States.DataSourceType.ExistingWallets
import org.elpaca_metagraph.shared_data.types.States.ExistingWalletsDataSource
import org.elpaca_metagraph.shared_data.types.WalletCreationHoldingDAG.WalletCreationHoldingDAGApiResponse
import org.http4s._
import org.http4s.circe._
import org.http4s.client.Client
import org.tessellation.node.shared.resources.MkHttpClient
import org.typelevel.ci.CIString
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.time.LocalDateTime

object WalletCreationHoldingDAGFetcher {

  def make[F[_] : Async : Network](
    applicationConfig     : ApplicationConfig,
    calculatedStateService: CalculatedStateService[F]
  ): Fetcher[F] =
    new Fetcher[F] {
      private val logger: SelfAwareStructuredLogger[F] = Slf4jLogger.getLoggerFromClass(IntegrationnetNodesOperatorsFetcher.getClass)

      def fetchLatestSnapshotBalances(): F[WalletCreationHoldingDAGApiResponse] = {
        val newWalletsDaemonConfig = applicationConfig.walletCreationHoldingDagDaemon
        val url = s"${newWalletsDaemonConfig.apiUrl.get}/global-snapshots/latest/combined"
        val clientResource: Resource[F, Client[F]] = MkHttpClient.forAsync[F].newEmber(applicationConfig.http4s.client)
        val acceptHeader = CIString("Accept")

        clientResource.use { client =>
          val request = Request[F](
            method = Method.GET,
            uri = Uri.unsafeFromString(url)
          ).withHeaders(Header.Raw(acceptHeader, s"application/json"))

          client.expect[WalletCreationHoldingDAGApiResponse](request)(jsonOf[F, WalletCreationHoldingDAGApiResponse])
        }
      }

      override def getAddressesAndBuildUpdates(currentDate: LocalDateTime): F[List[ElpacaUpdate]] =
        for {
          _ <- logger.info(s"Fetching wallets from global snapshots")
          integrationnetOperatorsApiResponse <- fetchLatestSnapshotBalances().handleErrorWith { err =>
            logger.error(s"Error when fetching wallets from global snapshots : ${err.getMessage}")
              .as(WalletCreationHoldingDAGApiResponse(Map.empty))
          }
          calculatedState <- calculatedStateService.get
          dataUpdates = calculatedState.state.dataSources.get(ExistingWallets)
            .fold(List.empty[ElpacaUpdate]) {
              case existingWallets: ExistingWalletsDataSource =>
                integrationnetOperatorsApiResponse.balances.foldLeft(List.empty[ElpacaUpdate]) { (acc, info) =>
                  val (address, balance) = info
                  if (existingWallets.existingWallets.get(address).exists(_.holdingDAGRewarded)) {
                    acc
                  } else {
                    acc :+ WalletCreationHoldingDAGUpdate(address, balance)
                  }
                }
              case _ => List.empty
            }
        } yield dataUpdates
    }
}

