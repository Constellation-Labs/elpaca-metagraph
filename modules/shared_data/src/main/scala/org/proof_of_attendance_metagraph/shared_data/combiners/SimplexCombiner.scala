package org.proof_of_attendance_metagraph.shared_data.combiners

import cats.effect.Async
import cats.syntax.applicativeError.catsSyntaxApplicativeErrorId
import cats.syntax.applicative._
import cats.syntax.functor._
import org.proof_of_attendance_metagraph.shared_data.Utils.toTokenAmountFormat
import org.proof_of_attendance_metagraph.shared_data.types.DataUpdates._
import org.proof_of_attendance_metagraph.shared_data.types.States.DataSourceType.DataSourceType
import org.proof_of_attendance_metagraph.shared_data.types.States._
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
    existing             : SimplexDataSourceAddress,
    newEventsIds         : Set[String],
    epochProgressToReward: EpochProgress
  ): Long = {
    if (epochProgressToReward.value.value < existing.epochProgressToReward.value.value) {
      (simplexRewardAmount * newEventsIds.size) + existing.amountToReward
    } else {
      simplexRewardAmount * newEventsIds.size
    }
  }

  private def updateSimplexDataSourceState[F[_] : Async : Logger](
    existing                : SimplexDataSourceAddress,
    simplexUpdate           : SimplexUpdate,
    currentSimplexDataSource: SimplexDataSource,
    epochProgressToReward   : EpochProgress,
  ): F[Map[Address, SimplexDataSourceAddress]] = {
    val newEventsIds = getNewEventIds(existing, simplexUpdate)

    if (newEventsIds.isEmpty) {
      currentSimplexDataSource.addresses.pure[F]
    } else {
      val rewardsAmount = calculateRewardsAmount(existing, newEventsIds, epochProgressToReward)

      val updatedSimplexDataSource = SimplexDataSourceAddress(
        epochProgressToReward,
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
    epochProgressToReward : EpochProgress,
    simplexUpdate         : SimplexUpdate
  ): F[Map[DataSourceType, DataSource]] = {

    getSimplexDataSourceUpdatedAddresses(currentCalculatedState, simplexUpdate, epochProgressToReward).map { updatedAddresses =>
      currentCalculatedState.updated(
        DataSourceType.Simplex,
        SimplexDataSource(updatedAddresses)
      )
    }
  }
}
