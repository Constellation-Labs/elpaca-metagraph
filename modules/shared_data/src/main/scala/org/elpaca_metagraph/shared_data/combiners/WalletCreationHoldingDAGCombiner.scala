package org.elpaca_metagraph.shared_data.combiners

import cats.syntax.all._
import monocle.Monocle.toAppliedFocusOps
import org.elpaca_metagraph.shared_data.Utils.toTokenAmountFormat
import org.elpaca_metagraph.shared_data.types.DataUpdates._
import org.elpaca_metagraph.shared_data.types.ExistingWallets.ExistingWalletsDataSourceAddress
import org.elpaca_metagraph.shared_data.types.States._
import org.elpaca_metagraph.shared_data.types.WalletCreationHoldingDAG.WalletCreationHoldingDAGDataSourceAddress
import org.tessellation.schema.address.Address
import org.tessellation.schema.epoch.EpochProgress

object WalletCreationHoldingDAGCombiner {
  private val walletCreationRewardAmount: Long = 10L
  private val fiveDaysEpochProgress: Long = 1440L * 5L

  private def addressHoldsDAGForAtLeast5Days(
    existing            : WalletCreationHoldingDAGDataSourceAddress,
    currentEpochProgress: EpochProgress
  ): Boolean =
    existing.registeredEpochProgress.value.value + fiveDaysEpochProgress < currentEpochProgress.value.value

  private def shouldMarkAddressAsRewarded(
    existingWalletsDataSource: ExistingWalletsDataSource,
    walletRewardAddress      : WalletCreationHoldingDAGDataSourceAddress,
    address                  : Address,
    currentEpochProgress     : EpochProgress,
  ): Boolean =
    existingWalletsDataSource.existingWallets.get(address).exists(_.holdingDAGRewarded) ||
      walletRewardAddress.balance < toTokenAmountFormat(1500) ||
      walletRewardAddress.epochProgressToReward.exists(epochProgress => epochProgress < currentEpochProgress)

  private def createWalletCreationHoldingDAGDataSourceAddress(
    currentEpochProgress: EpochProgress,
    walletCreationUpdate: WalletCreationHoldingDAGUpdate
  ): WalletCreationHoldingDAGDataSourceAddress = {
    WalletCreationHoldingDAGDataSourceAddress(
      none,
      toTokenAmountFormat(walletCreationRewardAmount),
      currentEpochProgress,
      walletCreationUpdate.balance
    )
  }

  private def updatedAddressToBeRewardedIfElegible(
    existing                       : WalletCreationHoldingDAGDataSourceAddress,
    walletCreationUpdate           : WalletCreationHoldingDAGUpdate,
    currentWalletCreationDataSource: WalletCreationHoldingDAGDataSource,
    currentEpochProgress           : EpochProgress
  ): Map[Address, WalletCreationHoldingDAGDataSourceAddress] = {
    val updatedExisting = existing.focus(_.balance).replace(walletCreationUpdate.balance)
    if (addressHoldsDAGForAtLeast5Days(existing, currentEpochProgress) && existing.epochProgressToReward.isEmpty) {
      currentWalletCreationDataSource.addressesToReward.updated(walletCreationUpdate.address, updatedExisting.focus(_.epochProgressToReward).replace(currentEpochProgress.some))
    } else {
      currentWalletCreationDataSource.addressesToReward.updated(walletCreationUpdate.address, updatedExisting)
    }
  }

  private def cleanRewardedAddresses(
    walletCreationHoldingDAGDataSource: WalletCreationHoldingDAGDataSource,
    existingWalletsDataSource         : ExistingWalletsDataSource,
    currentEpochProgress              : EpochProgress
  ): (WalletCreationHoldingDAGDataSource, ExistingWalletsDataSource) = {
    val addressesToReward = walletCreationHoldingDAGDataSource.addressesToReward
    val existingWallets = existingWalletsDataSource.existingWallets

    val (addressesToRewardUpdated, existingWalletsUpdated) = addressesToReward.foldLeft((addressesToReward, existingWallets)) { (acc, entry) =>
      val (address, walletCreationHoldingDAGDataSourceAddress) = entry
      val shouldRemoveAddress = shouldMarkAddressAsRewarded(
        existingWalletsDataSource,
        walletCreationHoldingDAGDataSourceAddress,
        address,
        currentEpochProgress
      )

      val existingWallet = acc._2.getOrElse(address, ExistingWalletsDataSourceAddress.empty)
      if (!shouldRemoveAddress) {
        (acc._1, acc._2.updated(address, existingWallet))
      } else {
        (acc._1 - address, acc._2.updated(address, existingWallet.focus(_.holdingDAGRewarded).replace(true)))
      }
    }

    (
      walletCreationHoldingDAGDataSource.focus(_.addressesToReward).replace(addressesToRewardUpdated),
      existingWalletsDataSource.focus(_.existingWallets).replace(existingWalletsUpdated)
    )
  }

  private def getExistingWalletsDataSource(
    currentCalculatedState: Map[DataSourceType, DataSource]
  ): ExistingWalletsDataSource =
    currentCalculatedState
      .getOrElse(DataSourceType.ExistingWallets, ExistingWalletsDataSource(Map.empty))
      .asInstanceOf[ExistingWalletsDataSource]

  private def getCurrentWalletCreationHoldingDAGDataSource(
    currentCalculatedState: Map[DataSourceType, DataSource],
  ): WalletCreationHoldingDAGDataSource = {
    currentCalculatedState
      .get(DataSourceType.WalletCreationHoldingDAG) match {
      case Some(walletCreationHoldingDAGDataSource: WalletCreationHoldingDAGDataSource) => walletCreationHoldingDAGDataSource
      case _ => WalletCreationHoldingDAGDataSource(Map.empty)
    }
  }

  def cleanWalletCreationHoldingDAGAlreadyRewardedWallets(
    currentCalculatedState: Map[DataSourceType, DataSource],
    currentEpochProgress  : EpochProgress
  ): Map[DataSourceType, DataSource] = {
    val existingWalletsDataSource = getExistingWalletsDataSource(currentCalculatedState)
    val existingWalletCreationHoldingDAGDataSource = getCurrentWalletCreationHoldingDAGDataSource(currentCalculatedState)

    val (existingWalletCreationHoldingDAGDataSourceUpdated, existingWalletsDataSourceUpdated) = cleanRewardedAddresses(
      existingWalletCreationHoldingDAGDataSource,
      existingWalletsDataSource,
      currentEpochProgress
    )
    currentCalculatedState
      .updated(DataSourceType.WalletCreationHoldingDAG, existingWalletCreationHoldingDAGDataSourceUpdated)
      .updated(DataSourceType.ExistingWallets, existingWalletsDataSourceUpdated)
  }

  def updateStateWalletCreationHoldingDAG(
    currentCalculatedState        : Map[DataSourceType, DataSource],
    currentEpochProgress          : EpochProgress,
    walletCreationHoldingDAGUpdate: WalletCreationHoldingDAGUpdate
  ): WalletCreationHoldingDAGDataSource = {
    val walletCreationHoldingDAGDataSourceAddress = createWalletCreationHoldingDAGDataSourceAddress(currentEpochProgress, walletCreationHoldingDAGUpdate)
    val walletCreationHoldingDAGDataSource = getCurrentWalletCreationHoldingDAGDataSource(currentCalculatedState)

    val walletCreationHoldingDAGDataSourceAddressesUpdated = walletCreationHoldingDAGDataSource.addressesToReward
      .get(walletCreationHoldingDAGUpdate.address) match {
      case Some(existingWalletCreationHoldingDAGDataSourceAddress) => updatedAddressToBeRewardedIfElegible(
        existingWalletCreationHoldingDAGDataSourceAddress,
        walletCreationHoldingDAGUpdate,
        walletCreationHoldingDAGDataSource,
        currentEpochProgress
      )
      case None => walletCreationHoldingDAGDataSource.addressesToReward.updated(walletCreationHoldingDAGUpdate.address, walletCreationHoldingDAGDataSourceAddress)
    }

    walletCreationHoldingDAGDataSource.focus(_.addressesToReward).replace(walletCreationHoldingDAGDataSourceAddressesUpdated)
  }
}
