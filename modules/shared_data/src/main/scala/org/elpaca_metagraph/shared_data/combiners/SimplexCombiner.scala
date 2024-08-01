package org.elpaca_metagraph.shared_data.combiners

import cats.effect.Async
import cats.syntax.all._
import org.elpaca_metagraph.shared_data.Utils.toTokenAmountFormat
import org.elpaca_metagraph.shared_data.types.DataUpdates.SimplexUpdate
import org.elpaca_metagraph.shared_data.types.Simplex._
import org.elpaca_metagraph.shared_data.types.States._
import org.tessellation.schema.address.Address
import org.tessellation.schema.epoch.EpochProgress
import org.typelevel.log4cats.Logger

object SimplexCombiner {
  private val simplexRewardAmount: Long = 35L

  private def getNewEventIds(
    existing     : SimplexDataSourceAddress,
    simplexUpdate: SimplexUpdate
  ): Set[String] = {
    val existingEventsIds = existing.latestTransactionsIds ++ existing.olderTransactionsIds
    simplexUpdate.simplexEvents.filterNot(evt => existingEventsIds.contains(evt.eventId)).map(_.eventId)
  }

  private def calculateRewardsAmount(
    existing            : SimplexDataSourceAddress,
    newEventsIds        : Set[String],
    currentEpochProgress: EpochProgress
  ): Long = {
    if (currentEpochProgress < existing.epochProgressToReward) {
      (simplexRewardAmount * newEventsIds.size) + existing.amountToReward.value.value
    } else {
      simplexRewardAmount * newEventsIds.size
    }
  }

  private def updateSimplexDataSourceState[F[_] : Async : Logger](
    existing                : SimplexDataSourceAddress,
    simplexUpdate           : SimplexUpdate,
    currentSimplexDataSource: SimplexDataSource,
    currentEpochProgress    : EpochProgress,
  ): F[Map[Address, SimplexDataSourceAddress]] = {
    val newEventsIds = getNewEventIds(existing, simplexUpdate)

    if (newEventsIds.isEmpty) {
      currentSimplexDataSource.addresses.pure[F]
    } else {
      val rewardsAmount = calculateRewardsAmount(existing, newEventsIds, currentEpochProgress)

      val updatedSimplexDataSource = SimplexDataSourceAddress(
        currentEpochProgress,
        toTokenAmountFormat(rewardsAmount),
        newEventsIds,
        existing.latestTransactionsIds ++ existing.olderTransactionsIds
      )

      Logger[F].info(s"Updated SimplexDataSource for address ${simplexUpdate.address}").as(
        currentSimplexDataSource.addresses.updated(simplexUpdate.address, updatedSimplexDataSource)
      )
    }
  }

  private def getSimplexDataSourceUpdatedAddresses[F[_] : Async : Logger](
    state        : Map[DataSourceType, DataSource],
    simplexUpdate: SimplexUpdate,
    epochProgress: EpochProgress
  ): F[Map[Address, SimplexDataSourceAddress]] = {
    val simplexDataSourceAddress = SimplexDataSourceAddress(
      epochProgress,
      toTokenAmountFormat(simplexRewardAmount * simplexUpdate.simplexEvents.size),
      simplexUpdate.simplexEvents.map(_.eventId),
      Set.empty[String]
    )

    state
      .get(DataSourceType.Simplex)
      .fold(Map(simplexUpdate.address -> simplexDataSourceAddress).pure[F]) {
        case simplexDataSource: SimplexDataSource =>
          simplexDataSource.addresses
            .get(simplexUpdate.address)
            .fold(simplexDataSource.addresses.updated(simplexUpdate.address, simplexDataSourceAddress).pure[F]) { existing =>
              updateSimplexDataSourceState(existing, simplexUpdate, simplexDataSource, epochProgress)
            }
        case _ => new IllegalStateException("DataSource is not from type SimplexDataSource").raiseError[F, Map[Address, SimplexDataSourceAddress]]
      }
  }

  def updateStateSimplexResponse[F[_] : Async : Logger](
    currentCalculatedState: Map[DataSourceType, DataSource],
    currentEpochProgress  : EpochProgress,
    simplexUpdate         : SimplexUpdate
  ): F[SimplexDataSource] =
    getSimplexDataSourceUpdatedAddresses(currentCalculatedState, simplexUpdate, currentEpochProgress).map { updatedAddresses =>
      SimplexDataSource(updatedAddresses)
    }
}
