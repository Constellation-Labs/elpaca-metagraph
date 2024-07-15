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
import org.elpaca_metagraph.shared_data.types.DataUpdates.{ElpacaUpdate, InflowTransactionsUpdate}
import org.elpaca_metagraph.shared_data.types.States.{DataSource, DataSourceType, InflowTransactionsDataSource}
import org.http4s._
import org.http4s.circe._
import org.http4s.client.Client
import org.tessellation.node.shared.resources.MkHttpClient
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, ZonedDateTime}

object InflowTransactionsFetcher {

  def make[F[_] : Async : Network](
    applicationConfig     : ApplicationConfig,
    calculatedStateService: CalculatedStateService[F]
  ): Fetcher[F] =
    new Fetcher[F] {
      private val logger: SelfAwareStructuredLogger[F] = Slf4jLogger.getLoggerFromClass(InflowTransactionsFetcher.getClass)

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

      private def getInflowWalletsDataSource(
        currentCalculatedState: Map[DataSourceType, DataSource],
      ): InflowTransactionsDataSource = {
        currentCalculatedState
          .get(DataSourceType.InflowTransactions) match {
          case Some(inflowWalletsDataSource: InflowTransactionsDataSource) => inflowWalletsDataSource
          case _ => InflowTransactionsDataSource(Map.empty)
        }
      }

      private def filterValidTransactions(
        inflowTransactionsResponse: BlockExplorerApiResponse,
        inflowWalletsDataSource   : InflowTransactionsDataSource,
        walletInfo                : WalletsInfo
      ) = {
        def sharedValidations(transaction: BlockExplorerTransaction): Boolean = {
          val timestampZ: ZonedDateTime = ZonedDateTime.parse(transaction.timestamp, DateTimeFormatter.ISO_ZONED_DATE_TIME)
          val timestampL: LocalDateTime = timestampZ.toLocalDateTime

          timestampL.isAfter(walletInfo.afterDate.atStartOfDay()) &&
            transaction.amount >= toTokenFormat(walletInfo.minimumAmount)
        }

        inflowTransactionsResponse.data.filter { transaction =>
          inflowWalletsDataSource.existingWallets.get(walletInfo.address) match {
            case None => sharedValidations(transaction)
            case Some(inflowDataSourceAddress) =>
              val existingHashes = inflowDataSourceAddress.addressesToReward.map(_.txnHash) ++ inflowDataSourceAddress.transactionsHashRewarded
              !existingHashes.contains(transaction.hash) && sharedValidations(transaction)
          }
        }
      }

      override def getAddressesAndBuildUpdates(currentDate: LocalDateTime): F[List[ElpacaUpdate]] = {
        val inflowConfig = applicationConfig.inflowTransactionsDaemon

        val walletsUpdates = inflowConfig.walletsInfo.traverse { walletInfo =>
          val url = s"${inflowConfig.apiUrl.get}/addresses/${walletInfo.address}/transactions/received?limit=10000"

          for {
            _ <- logger.info(s"Inflow - Fetching from block explorer using URL: $url")
            inflowTransactionsResponse <- fetchTransactions(url).handleErrorWith { err =>
              logger.error(s"Inflow - Error when fetching from block explorer API: ${err.getMessage}")
                .as(BlockExplorerApiResponse(List.empty[BlockExplorerTransaction]))
            }

            _ <- logger.info(s"Inflow - Found ${inflowTransactionsResponse.data.length} transactions")
            calculatedState <- calculatedStateService.get
            inflowWalletsDataSource = getInflowWalletsDataSource(calculatedState.state.dataSources)

            validTransactions = filterValidTransactions(inflowTransactionsResponse, inflowWalletsDataSource, walletInfo)
            _ <- logger.info(s"Inflow - Found ${validTransactions.length} valid transactions")

            dataUpdates = validTransactions.foldLeft(List.empty[ElpacaUpdate]) { (acc, beTransaction) =>
              acc :+ InflowTransactionsUpdate(walletInfo.address, beTransaction.hash, beTransaction.source, toTokenAmountFormat(walletInfo.rewardAmount))
            }
          } yield dataUpdates
        }
        walletsUpdates.map(_.flatten)
      }
    }
}
