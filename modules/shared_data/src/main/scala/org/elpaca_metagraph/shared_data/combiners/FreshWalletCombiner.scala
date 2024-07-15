package org.elpaca_metagraph.shared_data.combiners

import cats.syntax.all._
import monocle.Monocle.toAppliedFocusOps
import org.elpaca_metagraph.shared_data.Utils.toTokenAmountFormat
import org.elpaca_metagraph.shared_data.types.DataUpdates._
import org.elpaca_metagraph.shared_data.types.ExistingWallets.ExistingWalletsDataSourceAddress
import org.elpaca_metagraph.shared_data.types.FreshWallet.FreshWalletDataSourceAddress
import org.elpaca_metagraph.shared_data.types.States._
import org.tessellation.schema.address.Address
import org.tessellation.schema.epoch.EpochProgress

object FreshWalletCombiner {
  private val freshWalletRewardAmount: Long = 1L

  private def shouldMarkAddressAsRewarded(
    existingWalletsDataSource   : ExistingWalletsDataSource,
    freshWalletDataSourceAddress: FreshWalletDataSourceAddress,
    address                     : Address,
    currentEpochProgress        : EpochProgress,
  ): Boolean = {
    existingWalletsDataSource.existingWallets.get(address).exists(_.freshWalletRewarded) ||
      freshWalletDataSourceAddress.epochProgressToReward < currentEpochProgress
  }

  private def createFreshWalletDataSourceAddress(
    currentEpochProgress: EpochProgress
  ): FreshWalletDataSourceAddress = {
    FreshWalletDataSourceAddress(
      currentEpochProgress,
      toTokenAmountFormat(freshWalletRewardAmount)
    )
  }

  private def cleanRewardedAddresses(
    freshWalletDataSource    : FreshWalletDataSource,
    existingWalletsDataSource: ExistingWalletsDataSource,
    currentEpochProgress     : EpochProgress
  ): (FreshWalletDataSource, ExistingWalletsDataSource) = {
    val addressesToReward = freshWalletDataSource.addressesToReward
    val existingWallets = existingWalletsDataSource.existingWallets

    val (addressesToRewardUpdated, existingWalletsUpdated) = addressesToReward.foldLeft((addressesToReward, existingWallets)) { (acc, entry) =>
      val (address, freshWalletDataSourceAddress) = entry
      val shouldRemoveAddress = shouldMarkAddressAsRewarded(
        existingWalletsDataSource,
        freshWalletDataSourceAddress,
        address,
        currentEpochProgress
      )
      val existingWallet = acc._2.getOrElse(address, ExistingWalletsDataSourceAddress.empty)
      if (!shouldRemoveAddress) {
        (acc._1, acc._2.updated(address, existingWallet))
      } else {
        (acc._1 - address, acc._2.updated(address, existingWallet.focus(_.freshWalletRewarded).replace(true)))
      }
    }

    (
      freshWalletDataSource.focus(_.addressesToReward).replace(addressesToRewardUpdated),
      existingWalletsDataSource.focus(_.existingWallets).replace(existingWalletsUpdated)
    )
  }

  private def getExistingWalletsDataSource(
    currentCalculatedState: Map[DataSourceType, DataSource]
  ): ExistingWalletsDataSource =
    currentCalculatedState
      .getOrElse(DataSourceType.ExistingWallets, ExistingWalletsDataSource(Map.empty))
      .asInstanceOf[ExistingWalletsDataSource]

  private def getCurrentFreshWalletDataSource(
    currentCalculatedState: Map[DataSourceType, DataSource]
  ): FreshWalletDataSource = {
    currentCalculatedState
      .get(DataSourceType.FreshWallet) match {
      case Some(freshWalletDataSource: FreshWalletDataSource) => freshWalletDataSource
      case _ => FreshWalletDataSource(Map.empty)
    }
  }

  def cleanFreshWalletsAlreadyRewarded(
    currentCalculatedState: Map[DataSourceType, DataSource],
    currentEpochProgress  : EpochProgress
  ): Map[DataSourceType, DataSource] = {
    val existingWalletsDataSource = getExistingWalletsDataSource(currentCalculatedState)
    val existingFreshWalletDataSource = getCurrentFreshWalletDataSource(currentCalculatedState)

    val (existingFreshWalletDataSourceUpdated, existingWalletsDataSourceUpdated) = cleanRewardedAddresses(
      existingFreshWalletDataSource,
      existingWalletsDataSource,
      currentEpochProgress
    )
    currentCalculatedState
      .updated(DataSourceType.FreshWallet, existingFreshWalletDataSourceUpdated)
      .updated(DataSourceType.ExistingWallets, existingWalletsDataSourceUpdated)
  }

  def updateStateFreshWallet(
    currentCalculatedState: Map[DataSourceType, DataSource],
    currentEpochProgress  : EpochProgress,
    freshWalletUpdate     : FreshWalletUpdate
  ): FreshWalletDataSource = {
    val freshWalletDataSourceAddress = createFreshWalletDataSourceAddress(currentEpochProgress)
    val freshWalletDataSource = getCurrentFreshWalletDataSource(currentCalculatedState)

    val freshWalletDataSourceAddressesUpdated = freshWalletDataSource.addressesToReward
      .get(freshWalletUpdate.address) match {
      case Some(_) => freshWalletDataSource.addressesToReward
      case None => freshWalletDataSource.addressesToReward.updated(freshWalletUpdate.address, freshWalletDataSourceAddress)
    }

    freshWalletDataSource.focus(_.addressesToReward).replace(freshWalletDataSourceAddressesUpdated)
  }
}
