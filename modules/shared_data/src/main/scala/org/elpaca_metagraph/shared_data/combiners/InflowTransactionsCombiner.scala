package org.elpaca_metagraph.shared_data.combiners

import cats.syntax.all._
import monocle.Monocle.toAppliedFocusOps
import org.elpaca_metagraph.shared_data.types.DataUpdates._
import org.elpaca_metagraph.shared_data.types.InflowTransactions.{InflowAddressToRewardInfo, InflowTransactionsDataSourceAddress}
import org.elpaca_metagraph.shared_data.types.States._
import org.tessellation.schema.epoch.EpochProgress

object InflowTransactionsCombiner {
  private def createInflowAddressToRewardInfo(
    currentEpochProgress    : EpochProgress,
    inflowTransactionsUpdate: InflowTransactionsUpdate
  ): InflowAddressToRewardInfo = {
    InflowAddressToRewardInfo(
      inflowTransactionsUpdate.txnHash,
      inflowTransactionsUpdate.rewardAddress,
      currentEpochProgress,
      inflowTransactionsUpdate.rewardAmount
    )
  }

  private def cleanRewardedInflowTransactions(
    inflowTransactionsDataSource: InflowTransactionsDataSource,
    currentEpochProgress        : EpochProgress
  ): InflowTransactionsDataSource =
    inflowTransactionsDataSource.existingWallets.foldLeft(inflowTransactionsDataSource) { (acc, entry) =>
      val (address, inflowTransactionsDataSourceAddress) = entry
      val addressesToReward = inflowTransactionsDataSourceAddress.addressesToReward.filter(_.epochProgressToReward >= currentEpochProgress)
      val alreadyRewardedAddressesHashes = inflowTransactionsDataSourceAddress.addressesToReward
        .filter(_.epochProgressToReward < currentEpochProgress)
        .map(_.txnHash)

      val inflowTransactionsDataSourceAddressUpdated = inflowTransactionsDataSourceAddress
        .focus(_.addressesToReward)
        .replace(addressesToReward)
        .focus(_.transactionsHashRewarded)
        .replace(inflowTransactionsDataSourceAddress.transactionsHashRewarded ++ alreadyRewardedAddressesHashes)

      acc
        .focus(_.existingWallets)
        .modify(_.updated(address, inflowTransactionsDataSourceAddressUpdated))
    }

  private def getCurrentInflowTransactionsDataSource(
    currentCalculatedState: Map[DataSourceType, DataSource]
  ): InflowTransactionsDataSource = {
    currentCalculatedState
      .get(DataSourceType.InflowTransactions) match {
      case Some(inflowTransactionsDataSource: InflowTransactionsDataSource) => inflowTransactionsDataSource
      case _ => InflowTransactionsDataSource(Map.empty)
    }
  }

  def cleanInflowTransactionsRewarded(
    currentCalculatedState: Map[DataSourceType, DataSource],
    currentEpochProgress  : EpochProgress
  ): Map[DataSourceType, DataSource] = {
    val existingInflowTransactionsDataSource = getCurrentInflowTransactionsDataSource(currentCalculatedState)

    val inflowTransactionsDataSource = cleanRewardedInflowTransactions(
      existingInflowTransactionsDataSource,
      currentEpochProgress
    )
    currentCalculatedState
      .updated(DataSourceType.InflowTransactions, inflowTransactionsDataSource)
  }

  def updateStateInflowTransactions(
    currentCalculatedState  : Map[DataSourceType, DataSource],
    currentEpochProgress    : EpochProgress,
    inflowTransactionsUpdate: InflowTransactionsUpdate
  ): InflowTransactionsDataSource = {
    val inflowAddressToRewardInfo = createInflowAddressToRewardInfo(currentEpochProgress, inflowTransactionsUpdate)
    val inflowTransactionsDataSource = getCurrentInflowTransactionsDataSource(currentCalculatedState)

    val inflowTransactionsDataSourceAddress = inflowTransactionsDataSource.existingWallets
      .get(inflowTransactionsUpdate.address) match {
      case Some(inflowTransactionsDataSourceAddress) => inflowTransactionsDataSourceAddress
      case None => InflowTransactionsDataSourceAddress.empty
    }

    val existingHashes = inflowTransactionsDataSourceAddress.addressesToReward.map(_.txnHash) ++ inflowTransactionsDataSourceAddress.transactionsHashRewarded
    if (existingHashes.contains(inflowTransactionsUpdate.txnHash)) {
      inflowTransactionsDataSource
    } else {
      val addressesToReward = inflowAddressToRewardInfo +: inflowTransactionsDataSourceAddress.addressesToReward

      val inflowTransactionsDataSourceAddressUpdated = inflowTransactionsDataSourceAddress
        .focus(_.addressesToReward)
        .replace(addressesToReward)

      inflowTransactionsDataSource
        .focus(_.existingWallets)
        .modify(_.updated(inflowTransactionsUpdate.address, inflowTransactionsDataSourceAddressUpdated))
    }

  }
}
