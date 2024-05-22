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

object ExolixCombiner {
  private val exolix_reward_amount: Long = 35L

  private def updateExolixDataSourceState[F[_] : Async](
    existing            : ExolixDataSource,
    exolixUpdate        : ExolixUpdate,
    currentEpochProgress: EpochProgress,
    currentDataSources  : Set[DataSources],
    logger              : SelfAwareStructuredLogger[F]
  ): F[Set[DataSources]] = {
    val existingTxnsIds = existing.latestTransactionsIds ++ existing.olderTransactionsIds
    val newTxnsIds = exolixUpdate.exolixTransactions
      .filter(txn => !existingTxnsIds.contains(txn.id))
      .map(_.id)

    if (newTxnsIds.isEmpty) {
      currentDataSources.pure[F]
    } else {
      // This scenario is when we receive new transactions before pay the rewards
      val rewardsAmount = if (currentEpochProgress.value.value < existing.epochProgressToReward.value.value) {
        (exolix_reward_amount * newTxnsIds.size) + existing.amountToReward
      } else {
        exolix_reward_amount * newTxnsIds.size
      }

      val updatedExolixDataSource = ExolixDataSource(
        currentEpochProgress,
        toTokenAmountFormat(rewardsAmount),
        newTxnsIds,
        existing.latestTransactionsIds ++ existing.olderTransactionsIds
      )

      logger.info(s"Updated ExolixDataSource for address ${exolixUpdate.address}").as(
        currentDataSources - existing + updatedExolixDataSource
      )
    }
  }

  def updateStateExolixResponse[F[_] : Async](
    currentCalculatedState: Map[Address, Set[DataSources]],
    currentEpochProgress  : EpochProgress,
    exolixUpdate          : ExolixUpdate
  ): F[Map[Address, Set[DataSources]]] = {
    def logger: SelfAwareStructuredLogger[F] = Slf4jLogger.getLoggerFromName[F]("ExolixCombiner")

    val newExolixDataSource = ExolixDataSource(
      currentEpochProgress,
      toTokenAmountFormat(exolix_reward_amount * exolixUpdate.exolixTransactions.size),
      exolixUpdate.exolixTransactions.map(_.id),
      Set.empty[String]
    )

    val updatedDataSourcesF: F[Set[DataSources]] = currentCalculatedState.get(exolixUpdate.address) match {
      case None =>
        logger.info(s"Could not find any DataSource to the address ${exolixUpdate.address}, creating a new one").as(
          Set[DataSources](newExolixDataSource)
        )
      case Some(dataSources) =>
        dataSources.collectFirst {
          case ex: ExolixDataSource => ex
        }.fold(
          logger.info(s"Could not find ExolixDataSource to address ${exolixUpdate.address}, creating a new one").as(
            dataSources + newExolixDataSource
          )
        ) { existing =>
          updateExolixDataSourceState(
            existing,
            exolixUpdate,
            currentEpochProgress,
            dataSources,
            logger
          )
        }
    }
    updatedDataSourcesF.map(updatedDataSources => currentCalculatedState + (exolixUpdate.address -> updatedDataSources))
  }
}
