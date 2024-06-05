package org.elpaca_metagraph.shared_data.daemons.fetcher

import cats.effect.{Async, Resource}
import cats.syntax.all._
import fs2.io.net.Network
import io.circe.generic.auto._
import org.elpaca_metagraph.shared_data.app.ApplicationConfig
import org.elpaca_metagraph.shared_data.calculated_state.CalculatedStateService
import org.elpaca_metagraph.shared_data.types.DataUpdates.{ElpacaUpdate, WalletCreationUpdate}
import org.elpaca_metagraph.shared_data.types.States.DataSourceType.WalletCreation
import org.elpaca_metagraph.shared_data.types.States.WalletCreationDataSource
import org.elpaca_metagraph.shared_data.types.WalletCreationTypes.WalletCreationApiResponse
import org.http4s._
import org.http4s.circe._
import org.http4s.client.Client
import org.tessellation.node.shared.resources.MkHttpClient
import org.typelevel.ci.CIString
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

object WalletCreationFetcher {

  def make[F[_] : Async : Network](
    applicationConfig     : ApplicationConfig,
    calculatedStateService: CalculatedStateService[F]
  ): Fetcher[F] =
    new Fetcher[F] {
      private val logger: SelfAwareStructuredLogger[F] = Slf4jLogger.getLoggerFromClass(IntegrationnetNodesOperatorsFetcher.getClass)

      def fetchLatestSnapshotBalances(): F[WalletCreationApiResponse] = {
        val newWalletsDaemonConfig = applicationConfig.walletCreationDaemon
        val url = s"${newWalletsDaemonConfig.apiUrl.get}/global-snapshots/latest/combined"
        val clientResource: Resource[F, Client[F]] = MkHttpClient.forAsync[F].newEmber(applicationConfig.http4s.client)
        val acceptHeader = CIString("Accept")

        clientResource.use { client =>
          val request = Request[F](
            method = Method.GET,
            uri = Uri.unsafeFromString(url)
          ).withHeaders(Header.Raw(acceptHeader, s"application/json"))

          client.expect[WalletCreationApiResponse](request)(jsonOf[F, WalletCreationApiResponse])
        }
      }

      override def getAddressesAndBuildUpdates: F[List[ElpacaUpdate]] =
        for {
          _ <- logger.info(s"Fetching wallets from global snapshots")
          integrationnetOperatorsApiResponse <- fetchLatestSnapshotBalances().handleErrorWith { err =>
            logger.error(s"Error when fetching wallets from global snapshots : ${err.getMessage}")
              .as(WalletCreationApiResponse(Map.empty))
          }
          calculatedState <- calculatedStateService.get
          dataUpdates = calculatedState.state.dataSources.get(WalletCreation)
            .fold(List.empty[ElpacaUpdate]) {
              case walletCreationDataSource: WalletCreationDataSource =>
                integrationnetOperatorsApiResponse.balances.foldLeft(List.empty[ElpacaUpdate]) { (acc, info) =>
                  val (address, balance) = info
                  if (walletCreationDataSource.addressesRewarded.contains(address)) {
                    acc
                  } else {
                    acc :+ WalletCreationUpdate(address, balance)
                  }
                }
              case _ => List.empty
            }
        } yield dataUpdates
    }
}

