package org.elpaca_metagraph.shared_data.daemons.fetcher

import cats.effect.{Async, Resource}
import cats.syntax.all._
import fs2.io.net.Network
import io.circe.generic.auto._
import org.elpaca_metagraph.shared_data.Utils.{toTokenAmountFormat, toTokenFormat}
import org.elpaca_metagraph.shared_data.app.ApplicationConfig
import org.elpaca_metagraph.shared_data.app.ApplicationConfig.WalletsInfo
import org.elpaca_metagraph.shared_data.calculated_state.CalculatedStateService
import org.elpaca_metagraph.shared_data.types.ConstellationBlockExplorer.{BlockExplorerApiResponse, BlockExplorerTransaction}
import org.elpaca_metagraph.shared_data.types.DataUpdates.{ElpacaUpdate, OutflowTransactionsUpdate}
import org.elpaca_metagraph.shared_data.types.States.{DataSource, DataSourceType, OutflowTransactionsDataSource}
import org.http4s._
import org.http4s.circe._
import org.http4s.client.Client
import org.tessellation.node.shared.resources.MkHttpClient
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, ZonedDateTime}

object OutflowTransactionsFetcher {

  def make[F[_] : Async : Network](
    applicationConfig     : ApplicationConfig,
    calculatedStateService: CalculatedStateService[F]
  ): Fetcher[F] =
    new Fetcher[F] {
      private val logger: SelfAwareStructuredLogger[F] = Slf4jLogger.getLoggerFromClass(OutflowTransactionsFetcher.getClass)

      def fetchTransactions(url: String): F[BlockExplorerApiResponse] = {
        val clientResource: Resource[F, Client[F]] = MkHttpClient.forAsync[F].newEmber(applicationConfig.http4s.client)

        clientResource.use { client =>
          val request = Request[F](
            method = Method.GET,
            uri = Uri.unsafeFromString(url)
          )

          client.expect[BlockExplorerApiResponse](request)(jsonOf[F, BlockExplorerApiResponse])
        }
      }

      private def getOutflowWalletsDataSource(
        currentCalculatedState: Map[DataSourceType, DataSource],
      ): OutflowTransactionsDataSource = {
        currentCalculatedState
          .get(DataSourceType.OutflowTransactions) match {
          case Some(outflowWalletsDataSource: OutflowTransactionsDataSource) => outflowWalletsDataSource
          case _ => OutflowTransactionsDataSource(Map.empty)
        }
      }

      private def filterValidTransactions(
        outflowTransactionsResponse: BlockExplorerApiResponse,
        outflowWalletsDataSource   : OutflowTransactionsDataSource,
        walletInfo                 : WalletsInfo
      ) = {
        def sharedValidations(transaction: BlockExplorerTransaction): Boolean = {
          val timestampZ: ZonedDateTime = ZonedDateTime.parse(transaction.timestamp, DateTimeFormatter.ISO_ZONED_DATE_TIME)
          val timestampL: LocalDateTime = timestampZ.toLocalDateTime

          timestampL.isAfter(walletInfo.afterDate.atStartOfDay()) &&
            transaction.amount >= toTokenFormat(walletInfo.minimumAmount)
        }

        outflowTransactionsResponse.data.filter { transaction =>
          outflowWalletsDataSource.existingWallets.get(walletInfo.address) match {
            case None => sharedValidations(transaction)
            case Some(outflowDataSourceAddress) =>
              val existingHashes = outflowDataSourceAddress.addressesToReward.map(_.txnHash) ++ outflowDataSourceAddress.transactionsHashRewarded
              !existingHashes.contains(transaction.hash) && sharedValidations(transaction)
          }
        }
      }

      override def getAddressesAndBuildUpdates(currentDate: LocalDateTime): F[List[ElpacaUpdate]] = {
        val outflowConfig = applicationConfig.outflowTransactionsDaemon

        val walletsUpdates = outflowConfig.walletsInfo.traverse { walletInfo =>
          val url = s"${outflowConfig.apiUrl.get}/addresses/${walletInfo.address}/transactions/sent?limit=10000"

          for {
            _ <- logger.info(s"Outflow - Fetching from block explorer using URL: $url")
            outflowTransactionsResponse <- fetchTransactions(url).handleErrorWith { err =>
              logger.error(s"Outflow - Error when fetching from block explorer API: ${err.getMessage}")
                .as(BlockExplorerApiResponse(List.empty[BlockExplorerTransaction]))
            }

            _ <- logger.info(s"Outflow - Found ${outflowTransactionsResponse.data.length} transactions")
            calculatedState <- calculatedStateService.get

            outflowWalletsDataSource = getOutflowWalletsDataSource(calculatedState.state.dataSources)
            validTransactions = filterValidTransactions(outflowTransactionsResponse, outflowWalletsDataSource, walletInfo)

            _ <- logger.info(s"Outflow - Found ${validTransactions.length} new transactions")

            dataUpdates = validTransactions.foldLeft(List.empty[ElpacaUpdate]) { (acc, beTransaction) =>
              acc :+ OutflowTransactionsUpdate(walletInfo.address, beTransaction.hash, beTransaction.destination, toTokenAmountFormat(walletInfo.rewardAmount))
            }
          } yield dataUpdates
        }
        walletsUpdates.map(_.flatten)
      }
    }
}
