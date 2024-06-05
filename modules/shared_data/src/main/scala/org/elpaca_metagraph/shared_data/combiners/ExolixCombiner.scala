package org.elpaca_metagraph.shared_data.combiners

import cats.effect.Async
import cats.syntax.all._
import org.elpaca_metagraph.shared_data.Utils.toTokenAmountFormat
import org.elpaca_metagraph.shared_data.types.DataUpdates.ExolixUpdate
import org.elpaca_metagraph.shared_data.types.States.{DataSource, DataSourceType, ExolixDataSource, ExolixDataSourceAddress}
import org.tessellation.schema.address.Address
import org.tessellation.schema.epoch.EpochProgress
import org.typelevel.log4cats.Logger

object ExolixCombiner {
  private val exolixRewardAmount: Long = 35L

  private def getNewTransactionsIds(
    existing    : ExolixDataSourceAddress,
    exolixUpdate: ExolixUpdate
  ): Set[String] = {
    val existingTxnsIds = existing.latestTransactionsIds ++ existing.olderTransactionsIds
    exolixUpdate.exolixTransactions.filterNot(txn => existingTxnsIds.contains(txn.id)).map(_.id)
  }

  private def calculateRewardsAmount(
    existing             : ExolixDataSourceAddress,
    newTxnsIds           : Set[String],
    epochProgressToReward: EpochProgress): Long = {
    if (epochProgressToReward.value.value < existing.epochProgressToReward.value.value) {
      (exolixRewardAmount * newTxnsIds.size) + existing.amountToReward
    } else {
      exolixRewardAmount * newTxnsIds.size
    }
  }

  private def updateExolixDataSourceState[F[_] : Async : Logger](
    existing               : ExolixDataSourceAddress,
    exolixUpdate           : ExolixUpdate,
    currentExolixDataSource: ExolixDataSource,
    epochProgressToReward  : EpochProgress
  ): F[Map[Address, ExolixDataSourceAddress]] = {
    val newTxnsIds = getNewTransactionsIds(existing, exolixUpdate)

    if (newTxnsIds.isEmpty) {
      currentExolixDataSource.addresses.pure[F]
    } else {
      val rewardsAmount = calculateRewardsAmount(existing, newTxnsIds, epochProgressToReward)

      val updatedExolixDataSource = ExolixDataSourceAddress(
        epochProgressToReward,
        toTokenAmountFormat(rewardsAmount),
        newTxnsIds,
        existing.latestTransactionsIds ++ existing.olderTransactionsIds
      )

      Logger[F].info(s"Updated ExolixDataSource for address ${exolixUpdate.address}").as(
        currentExolixDataSource.addresses.updated(exolixUpdate.address, updatedExolixDataSource)
      )
    }
  }

  private def getExolixDataSourceUpdatedAddresses[F[_] : Async : Logger](
    state        : Map[DataSourceType, DataSource],
    exolixUpdate : ExolixUpdate,
    epochProgress: EpochProgress
  ): F[Map[Address, ExolixDataSourceAddress]] = {
    val exolixDataSourceAddress = ExolixDataSourceAddress(
      epochProgress,
      toTokenAmountFormat(exolixRewardAmount * exolixUpdate.exolixTransactions.size),
      exolixUpdate.exolixTransactions.map(_.id),
      Set.empty[String]
    )

    state
      .get(DataSourceType.Exolix)
      .fold(Map(exolixUpdate.address -> exolixDataSourceAddress).pure[F]) {
        case exolixDataSource: ExolixDataSource =>
          exolixDataSource.addresses
            .get(exolixUpdate.address)
            .fold(exolixDataSource.addresses.updated(exolixUpdate.address, exolixDataSourceAddress).pure[F]) { existing =>
              updateExolixDataSourceState(existing, exolixUpdate, exolixDataSource, epochProgress)
            }
        case _ => new IllegalStateException("DataSource is not from type ExolixDataSource").raiseError[F, Map[Address, ExolixDataSourceAddress]]
      }
  }

  def updateStateExolixResponse[F[_] : Async : Logger](
    currentCalculatedState: Map[DataSourceType, DataSource],
    epochProgressToReward : EpochProgress,
    exolixUpdate          : ExolixUpdate
  ): F[Map[DataSourceType, DataSource]] = {
    getExolixDataSourceUpdatedAddresses(currentCalculatedState, exolixUpdate, epochProgressToReward).map { updatedAddresses =>
      currentCalculatedState.updated(
        DataSourceType.Exolix,
        ExolixDataSource(updatedAddresses)
      )
    }
  }
}
