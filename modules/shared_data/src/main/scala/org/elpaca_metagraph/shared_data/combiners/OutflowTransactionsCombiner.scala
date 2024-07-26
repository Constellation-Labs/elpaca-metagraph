package org.elpaca_metagraph.shared_data.combiners

import cats.syntax.all._
import monocle.Monocle.toAppliedFocusOps
import org.elpaca_metagraph.shared_data.types.DataUpdates._
import org.elpaca_metagraph.shared_data.types.OutflowTransactions.{OutflowAddressToRewardInfo, OutflowTransactionsDataSourceAddress}
import org.elpaca_metagraph.shared_data.types.States._
import org.tessellation.schema.epoch.EpochProgress

object OutflowTransactionsCombiner {
  private def createOutflowAddressToRewardInfo(
    currentEpochProgress     : EpochProgress,
    outflowTransactionsUpdate: OutflowTransactionsUpdate
  ): OutflowAddressToRewardInfo = {
    OutflowAddressToRewardInfo(
      outflowTransactionsUpdate.txnHash,
      outflowTransactionsUpdate.rewardAddress,
      currentEpochProgress,
      outflowTransactionsUpdate.rewardAmount
    )
  }

  private def cleanRewardedOutflowTransactions(
    outflowTransactionsDataSource: OutflowTransactionsDataSource,
    currentEpochProgress         : EpochProgress
  ): OutflowTransactionsDataSource =
    outflowTransactionsDataSource.existingWallets.foldLeft(outflowTransactionsDataSource) { (acc, entry) =>
      val (address, outflowTransactionsDataSourceAddress) = entry
      val addressesToReward = outflowTransactionsDataSourceAddress.addressesToReward.filter(_.epochProgressToReward >= currentEpochProgress)
      val alreadyRewardedAddressesHashes = outflowTransactionsDataSourceAddress.addressesToReward
        .filter(_.epochProgressToReward < currentEpochProgress)
        .map(_.txnHash)

      val outflowTransactionsDataSourceAddressUpdated = outflowTransactionsDataSourceAddress
        .focus(_.addressesToReward)
        .replace(addressesToReward)
        .focus(_.transactionsHashRewarded)
        .replace(outflowTransactionsDataSourceAddress.transactionsHashRewarded ++ alreadyRewardedAddressesHashes)

      acc
        .focus(_.existingWallets)
        .modify(_.updated(address, outflowTransactionsDataSourceAddressUpdated))
    }

  private def getCurrentOutflowTransactionsDataSource(
    currentCalculatedState: Map[DataSourceType, DataSource]
  ): OutflowTransactionsDataSource = {
    currentCalculatedState
      .get(DataSourceType.OutflowTransactions) match {
      case Some(outflowTransactionsDataSource: OutflowTransactionsDataSource) => outflowTransactionsDataSource
      case _ => OutflowTransactionsDataSource(Map.empty)
    }
  }

  def cleanOutflowTransactionsRewarded(
    currentCalculatedState: Map[DataSourceType, DataSource],
    currentEpochProgress  : EpochProgress
  ): Map[DataSourceType, DataSource] = {
    val existingOutflowTransactionsDataSource = getCurrentOutflowTransactionsDataSource(currentCalculatedState)

    val outflowTransactionsDataSource = cleanRewardedOutflowTransactions(
      existingOutflowTransactionsDataSource,
      currentEpochProgress
    )
    currentCalculatedState
      .updated(DataSourceType.OutflowTransactions, outflowTransactionsDataSource)
  }

  def updateStateOutflowTransactions(
    currentCalculatedState   : Map[DataSourceType, DataSource],
    currentEpochProgress     : EpochProgress,
    outflowTransactionsUpdate: OutflowTransactionsUpdate
  ): OutflowTransactionsDataSource = {
    val outflowAddressToRewardInfo = createOutflowAddressToRewardInfo(currentEpochProgress, outflowTransactionsUpdate)
    val outflowTransactionsDataSource = getCurrentOutflowTransactionsDataSource(currentCalculatedState)

    val outflowTransactionsDataSourceAddress = outflowTransactionsDataSource.existingWallets
      .get(outflowTransactionsUpdate.address) match {
      case Some(outflowTransactionsDataSourceAddress) => outflowTransactionsDataSourceAddress
      case None => OutflowTransactionsDataSourceAddress.empty
    }

    val existingHashes = outflowTransactionsDataSourceAddress.addressesToReward.map(_.txnHash) ++ outflowTransactionsDataSourceAddress.transactionsHashRewarded
    if (existingHashes.contains(outflowTransactionsUpdate.txnHash)) {
      outflowTransactionsDataSource
    } else {
      val addressesToReward = outflowAddressToRewardInfo +: outflowTransactionsDataSourceAddress.addressesToReward
      val outflowTransactionsDataSourceAddressUpdated = outflowTransactionsDataSourceAddress
        .focus(_.addressesToReward)
        .replace(addressesToReward)

      outflowTransactionsDataSource
        .focus(_.existingWallets)
        .modify(_.updated(outflowTransactionsUpdate.address, outflowTransactionsDataSourceAddressUpdated))
    }
  }
}
