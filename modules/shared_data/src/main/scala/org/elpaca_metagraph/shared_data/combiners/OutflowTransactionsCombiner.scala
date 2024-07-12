package org.elpaca_metagraph.shared_data.combiners

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
      val addressesToReward = outflowTransactionsDataSourceAddress.addressesToReward.filter(_.epochProgressToReward.value.value >= currentEpochProgress.value.value)
      val alreadyRewardedAddressesHashes = outflowTransactionsDataSourceAddress.addressesToReward
        .filter(_.epochProgressToReward.value.value < currentEpochProgress.value.value)
        .map(_.txnHash)

      val outflowTransactionsDataSourceAddressUpdated = outflowTransactionsDataSourceAddress
        .copy(addressesToReward = addressesToReward, transactionsHashRewarded = outflowTransactionsDataSourceAddress.transactionsHashRewarded ++ alreadyRewardedAddressesHashes)

      acc.copy(existingWallets = acc.existingWallets.updated(address, outflowTransactionsDataSourceAddressUpdated))
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
  ): Map[DataSourceType, DataSource] = {
    val outflowAddressToRewardInfo = createOutflowAddressToRewardInfo(currentEpochProgress, outflowTransactionsUpdate)
    val outflowTransactionsDataSource = getCurrentOutflowTransactionsDataSource(currentCalculatedState)

    val outflowTransactionsDataSourceAddress = outflowTransactionsDataSource.existingWallets
      .get(outflowTransactionsUpdate.address) match {
      case Some(outflowTransactionsDataSourceAddress) => outflowTransactionsDataSourceAddress
      case None => OutflowTransactionsDataSourceAddress.empty
    }

    val existingHashes = outflowTransactionsDataSourceAddress.addressesToReward.map(_.txnHash) ++ outflowTransactionsDataSourceAddress.transactionsHashRewarded
    if (existingHashes.contains(outflowTransactionsUpdate.txnHash)) {
      currentCalculatedState
    } else {
      val addressesToReward = outflowAddressToRewardInfo +: outflowTransactionsDataSourceAddress.addressesToReward
      val outflowTransactionsDataSourceUpdated = outflowTransactionsDataSource.existingWallets.updated(outflowTransactionsUpdate.address, outflowTransactionsDataSourceAddress.copy(addressesToReward = addressesToReward))
      currentCalculatedState
        .updated(DataSourceType.OutflowTransactions, outflowTransactionsDataSource.copy(existingWallets = outflowTransactionsDataSourceUpdated))
    }
  }
}
