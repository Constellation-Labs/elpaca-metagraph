package org.proof_of_attendance_metagraph.shared_data.combiners

import cats.effect.Async
import cats.syntax.applicative._
import cats.syntax.functor._
import org.proof_of_attendance_metagraph.shared_data.Utils.toTokenAmountFormat
import org.proof_of_attendance_metagraph.shared_data.types.DataUpdates._
import org.proof_of_attendance_metagraph.shared_data.types.States._
import org.tessellation.schema.address.Address
import org.tessellation.schema.epoch.EpochProgress
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

object SimplexCombiner {
  private val simplex_reward_amount: Long = 35L

  private def updateSimplexDataSourceState[F[_] : Async](
    existing            : SimplexDataSource,
    simplexUpdate       : SimplexUpdate,
    currentEpochProgress: EpochProgress,
    currentDataSources  : Set[DataSources],
    logger              : SelfAwareStructuredLogger[F]
  ): F[Set[DataSources]] = {
    val existingTxnsIds = existing.latestEventsIds ++ existing.olderEventsIds
    val newTxnsIds = simplexUpdate.simplexEvents
      .filter(txn => !existingTxnsIds.contains(txn.eventId))
      .map(_.eventId)

    if (newTxnsIds.isEmpty) {
      currentDataSources.pure[F]
    } else {
      // This scenario is when we receive new transactions before pay the rewards
      val rewardsAmount = if (currentEpochProgress.value.value < existing.epochProgressToReward.value.value) {
        (simplex_reward_amount * newTxnsIds.size) + existing.amountToReward
      } else {
        simplex_reward_amount * newTxnsIds.size
      }

      val updatedSimplexDataSource = SimplexDataSource(
        currentEpochProgress,
        toTokenAmountFormat(rewardsAmount),
        newTxnsIds,
        existing.latestEventsIds ++ existing.olderEventsIds
      )

      logger.info(s"Updated SimplexDataSource for address ${simplexUpdate.address}").as(
        currentDataSources - existing + updatedSimplexDataSource
      )
    }
  }

  def updateStateSimplexResponse[F[_] : Async](
    currentCalculatedState: Map[Address, Set[DataSources]],
    currentEpochProgress  : EpochProgress,
    simplexUpdate         : SimplexUpdate
  ): F[Map[Address, Set[DataSources]]] = {
    def logger: SelfAwareStructuredLogger[F] = Slf4jLogger.getLoggerFromName[F]("SimplexCombiner")

    val newSimplexDataSource = SimplexDataSource(
      currentEpochProgress,
      toTokenAmountFormat(simplex_reward_amount * simplexUpdate.simplexEvents.size),
      simplexUpdate.simplexEvents.map(_.eventId),
      Set.empty[String]
    )

    val updatedDataSourcesF: F[Set[DataSources]] = currentCalculatedState.get(simplexUpdate.address) match {
      case None =>
        logger.info(s"Could not find any DataSource to the address ${simplexUpdate.address}, creating a new one").as(
          Set[DataSources](newSimplexDataSource)
        )
      case Some(dataSources) =>
        dataSources.collectFirst {
          case ex: SimplexDataSource => ex
        }.fold(
          logger.info(s"Could not find SimplexDataSource to address ${simplexUpdate.address}, creating a new one").as(
            dataSources + newSimplexDataSource
          )
        ) { existing =>
          updateSimplexDataSourceState(
            existing,
            simplexUpdate,
            currentEpochProgress,
            dataSources,
            logger
          )
        }
    }
    updatedDataSourcesF.map(updatedDataSources => currentCalculatedState + (simplexUpdate.address -> updatedDataSources))
  }
}
