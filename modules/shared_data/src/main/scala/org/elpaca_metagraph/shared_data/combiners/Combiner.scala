package org.elpaca_metagraph.shared_data.combiners

import cats.effect.Async
import cats.syntax.all._
import org.elpaca_metagraph.shared_data.app.ApplicationConfig
import org.elpaca_metagraph.shared_data.combiners.ExolixCombiner.updateStateExolixResponse
import org.elpaca_metagraph.shared_data.combiners.FreshWalletCombiner.updateStateFreshWallet
import org.elpaca_metagraph.shared_data.combiners.InflowTransactionsCombiner.updateStateInflowTransactions
import org.elpaca_metagraph.shared_data.combiners.IntegrationnetOperatorsCombiner.updateStateIntegrationnetOperatorsResponse
import org.elpaca_metagraph.shared_data.combiners.OutflowTransactionsCombiner.updateStateOutflowTransactions
import org.elpaca_metagraph.shared_data.combiners.SimplexCombiner.updateStateSimplexResponse
import org.elpaca_metagraph.shared_data.combiners.WalletCreationHoldingDAGCombiner.updateStateWalletCreationHoldingDAG
import org.elpaca_metagraph.shared_data.combiners.XCombiner.updateStateX
import org.elpaca_metagraph.shared_data.types.DataUpdates._
import org.elpaca_metagraph.shared_data.types.States._
import org.tessellation.currency.dataApplication.DataState
import org.tessellation.schema.epoch.EpochProgress
import org.tessellation.security.signature.Signed
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

object Combiner {
  def combineElpacaUpdate[F[_] : Async](
    oldState            : DataState[ElpacaOnChainState, ElpacaCalculatedState],
    currentEpochProgress: EpochProgress,
    update              : Signed[ElpacaUpdate],
    applicationConfig   : ApplicationConfig
  ): F[DataState[ElpacaOnChainState, ElpacaCalculatedState]] = {
    val currentDataSources = oldState.calculated.dataSources

    val (dataSourceType, updatedDataSourceF): (DataSourceType, F[DataSource]) = update.value match {
      case update: ExolixUpdate =>
        implicit val logger: SelfAwareStructuredLogger[F] = Slf4jLogger.getLoggerFromName[F]("ExolixCombiner")
        val exolixDataSourceUpdated = updateStateExolixResponse(
          currentDataSources,
          currentEpochProgress,
          update
        ).map(_.asInstanceOf[DataSource])

        (DataSourceType.Exolix, exolixDataSourceUpdated)

      case update: SimplexUpdate =>
        implicit val logger: SelfAwareStructuredLogger[F] = Slf4jLogger.getLoggerFromName[F]("SimplexCombiner")
        val simplexDataSourceUpdated = updateStateSimplexResponse(
          currentDataSources,
          currentEpochProgress,
          update
        ).map(_.asInstanceOf[DataSource])

        (DataSourceType.Simplex, simplexDataSourceUpdated)

      case update: IntegrationnetNodeOperatorUpdate =>
        implicit val logger: SelfAwareStructuredLogger[F] = Slf4jLogger.getLoggerFromName[F]("IntegrationnetOperatorsCombiner")
        val integrationnetDataSourceUpdated = updateStateIntegrationnetOperatorsResponse(
          currentDataSources,
          currentEpochProgress,
          update
        ).map(_.asInstanceOf[DataSource])

        (DataSourceType.IntegrationnetNodeOperator, integrationnetDataSourceUpdated)

      case update: WalletCreationHoldingDAGUpdate =>
        val updatedWalletCreationHoldingDAGDataSource = updateStateWalletCreationHoldingDAG(
          currentDataSources,
          currentEpochProgress,
          update
        ).asInstanceOf[DataSource]

        (DataSourceType.WalletCreationHoldingDAG, updatedWalletCreationHoldingDAGDataSource.pure)

      case update: FreshWalletUpdate =>
        val freshWalletDataSourceUpdate = updateStateFreshWallet(
          oldState.calculated.dataSources,
          currentEpochProgress,
          update
        ).asInstanceOf[DataSource]

        (DataSourceType.WalletCreationHoldingDAG, freshWalletDataSourceUpdate.pure)

      case update: InflowTransactionsUpdate =>
        val updatedInflowTransactionsDataSource = updateStateInflowTransactions(
          currentDataSources,
          currentEpochProgress,
          update
        ).asInstanceOf[DataSource]

        (DataSourceType.InflowTransactions, updatedInflowTransactionsDataSource.pure)

      case update: OutflowTransactionsUpdate =>
        val updatedOutflowTransactionsDataSource = updateStateOutflowTransactions(
          currentDataSources,
          currentEpochProgress,
          update
        ).asInstanceOf[DataSource]

        (DataSourceType.OutflowTransactions, updatedOutflowTransactionsDataSource.pure)

      case update: XUpdate =>
        val updatedXDataSource = updateStateX(
          currentDataSources,
          currentEpochProgress,
          update,
          applicationConfig
        ).asInstanceOf[DataSource]

        (DataSourceType.X, updatedXDataSource.pure)
    }
    updatedDataSourceF.map { updatedDataSource =>
      val updates: List[ElpacaUpdate] = update.value :: oldState.onChain.updates
      DataState(
        ElpacaOnChainState(updates),
        ElpacaCalculatedState(
          currentDataSources.updated(
            dataSourceType,
            updatedDataSource
          )
        )
      )
    }
  }
}