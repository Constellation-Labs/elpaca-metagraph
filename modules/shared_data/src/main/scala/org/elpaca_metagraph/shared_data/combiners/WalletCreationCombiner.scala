package org.elpaca_metagraph.shared_data.combiners

import cats.effect.Async
import cats.syntax.all._
import org.elpaca_metagraph.shared_data.Utils.toTokenAmountFormat
import org.elpaca_metagraph.shared_data.types.DataUpdates._
import org.elpaca_metagraph.shared_data.types.States._
import org.tessellation.schema.epoch.EpochProgress
import org.typelevel.log4cats.Logger

object WalletCreationCombiner {
  private val walletCreationRewardAmount: Long = 10L
  private val fiveDaysEpochProgress: Long = 1440L * 5

  private def addressHoldsDAGForAtLeast5Days(existing: WalletCreationDataSourceAddress, epochProgressToReward: EpochProgress): Boolean =
    existing.registeredEpochProgress.value.value + fiveDaysEpochProgress < epochProgressToReward.value.value

  private def moveAddressToAlreadyRewarded(walletCreationDataSource: WalletCreationDataSource, walletCreationUpdate: WalletCreationUpdate) = {
    walletCreationDataSource.copy(
      addressesToReward = walletCreationDataSource.addressesToReward.removed(walletCreationUpdate.address),
      addressesRewarded = walletCreationDataSource.addressesRewarded + walletCreationUpdate.address
    )
  }

  private def addWalletToBeRewarded(
    walletCreationDataSource       : WalletCreationDataSource,
    walletCreationUpdate           : WalletCreationUpdate,
    walletCreationDataSourceAddress: WalletCreationDataSourceAddress
  ): WalletCreationDataSource =
    walletCreationDataSource.copy(
      addressesToReward = walletCreationDataSource.addressesToReward.updated(walletCreationUpdate.address, walletCreationDataSourceAddress)
    )

  private def createWalletCreationDataSourceAddress(
    epochProgressToReward: EpochProgress,
    walletCreationUpdate : WalletCreationUpdate
  ): WalletCreationDataSourceAddress = {
    WalletCreationDataSourceAddress(
      none,
      toTokenAmountFormat(walletCreationRewardAmount),
      epochProgressToReward,
      walletCreationUpdate.balance
    )
  }

  private def updatedAddressToBeRewardedIfElegible[F[_] : Async : Logger](
    existing                       : WalletCreationDataSourceAddress,
    walletCreationUpdate           : WalletCreationUpdate,
    currentWalletCreationDataSource: WalletCreationDataSource,
    epochProgressToReward          : EpochProgress
  ): F[WalletCreationDataSource] = {
    if (addressHoldsDAGForAtLeast5Days(existing, epochProgressToReward)) {
      val updatedWalletCreationDataSourceAddress = existing.copy(epochProgressToReward = epochProgressToReward.some)
      Logger[F].info(s"Updated WalletCreationDataSource for address ${walletCreationUpdate.address}").as(
        currentWalletCreationDataSource.copy(
          addressesToReward = currentWalletCreationDataSource.addressesToReward.updated(walletCreationUpdate.address, updatedWalletCreationDataSourceAddress)
        )
      )
    } else {
      currentWalletCreationDataSource.pure
    }
  }

  private def handleWalletCreation[F[_] : Async : Logger](
    walletCreationDataSource       : WalletCreationDataSource,
    walletCreationUpdate           : WalletCreationUpdate,
    walletCreationDataSourceAddress: WalletCreationDataSourceAddress,
    epochProgressToReward          : EpochProgress
  ): F[WalletCreationDataSource] = {
    walletCreationDataSource.addressesToReward.get(walletCreationUpdate.address).fold(
      Async[F].delay(addWalletToBeRewarded(walletCreationDataSource, walletCreationUpdate, walletCreationDataSourceAddress))
    )(existing =>
      if (existing.epochProgressToReward.exists(epochProgress => epochProgress.value.value >= epochProgressToReward.value.value)) {
        walletCreationDataSource.pure
      } else if (walletCreationDataSource.addressesRewarded.contains(walletCreationUpdate.address) ||
        walletCreationUpdate.balance < toTokenAmountFormat(1500) ||
        existing.epochProgressToReward.exists(epochProgress => epochProgress.value.value < epochProgressToReward.value.value)
      ) {
        Async[F].delay(moveAddressToAlreadyRewarded(walletCreationDataSource, walletCreationUpdate))
      } else {
        updatedAddressToBeRewardedIfElegible(existing, walletCreationUpdate, walletCreationDataSource, epochProgressToReward)
      }
    )
  }

  def updateStateWalletCreation[F[_] : Async : Logger](
    currentCalculatedState: Map[DataSourceType, DataSource],
    epochProgressToReward : EpochProgress,
    walletCreationUpdate  : WalletCreationUpdate
  ): F[Map[DataSourceType, DataSource]] = {
    val walletCreationDataSourceAddress = createWalletCreationDataSourceAddress(epochProgressToReward, walletCreationUpdate)

    val updatedWalletCreationDataSourceF: F[WalletCreationDataSource] = currentCalculatedState
      .get(DataSourceType.WalletCreation)
      .fold(
        WalletCreationDataSource(Map(walletCreationUpdate.address -> walletCreationDataSourceAddress), Set.empty).pure
      ) {
        case walletCreationDataSource: WalletCreationDataSource =>
          handleWalletCreation(
            walletCreationDataSource,
            walletCreationUpdate,
            walletCreationDataSourceAddress,
            epochProgressToReward
          )
        case _ => new IllegalStateException("DataSource is not from type WalletCreationDataSource").raiseError[F, WalletCreationDataSource]
      }

    updatedWalletCreationDataSourceF.map { updatedWalletCreationDataSource =>
      val addressesToRemove = updatedWalletCreationDataSource.addressesToReward.keys
        .toList
        .filter(updatedWalletCreationDataSource.addressesRewarded.contains)

      val updatedAddressesToReward = addressesToRemove.foldLeft(updatedWalletCreationDataSource.addressesToReward) {
        (map, address) => map - address
      }

      val newUpdatedWalletCreationDataSource = updatedWalletCreationDataSource.copy(
        addressesToReward = updatedAddressesToReward
      )

      currentCalculatedState.updated(DataSourceType.WalletCreation, newUpdatedWalletCreationDataSource)
    }

  }
}
