package org.proof_of_attendance_metagraph.shared_data.combiners

import cats.effect.Async
import cats.syntax.applicative.catsSyntaxApplicativeId
import cats.syntax.functor.toFunctorOps
import org.proof_of_attendance_metagraph.shared_data.Utils.toTokenAmountFormat
import org.proof_of_attendance_metagraph.shared_data.types.DataUpdates._
import org.proof_of_attendance_metagraph.shared_data.types.States._
import org.tessellation.schema.address.Address
import org.tessellation.schema.epoch.EpochProgress
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

object IntegrationnetOperatorsCombiner {
  private val integrationnet_reward_amount: Long = 1L

  private def updateIntegrationnetOpDataSourceState[F[_] : Async](
    existing                        : IntegrationnetNodeOperatorDataSource,
    integrationnetNodeOperatorUpdate: IntegrationnetNodeOperatorUpdate,
    currentEpochProgress            : EpochProgress,
    currentDataSources              : Set[DataSources],
    logger                          : SelfAwareStructuredLogger[F]
  ): F[Set[DataSources]] = {
    val daysInQueue = integrationnetNodeOperatorUpdate.operatorInQueue.daysInQueue - existing.daysInQueue
    if (daysInQueue <= 0L) {
      currentDataSources.pure
    } else {
      val updatedExolixDataSource = IntegrationnetNodeOperatorDataSource(
        currentEpochProgress,
        toTokenAmountFormat(
          integrationnet_reward_amount * daysInQueue
        ),
        integrationnetNodeOperatorUpdate.operatorInQueue.daysInQueue
      )

      logger.info(s"Updated IntegrationnetNodeOperatorDataSource for address ${integrationnetNodeOperatorUpdate.address}").as(
        currentDataSources - existing + updatedExolixDataSource
      )
    }
  }

  def updateStateIntegrationnetOperatorsResponse[F[_] : Async](
    currentCalculatedState          : Map[Address, Set[DataSources]],
    currentEpochProgress            : EpochProgress,
    integrationnetNodeOperatorUpdate: IntegrationnetNodeOperatorUpdate
  ): F[Map[Address, Set[DataSources]]] = {
    def logger: SelfAwareStructuredLogger[F] = Slf4jLogger.getLoggerFromName[F]("IntegrationnetOperatorsCombiner")

    val newIntegrationnetDataSource = IntegrationnetNodeOperatorDataSource(
      currentEpochProgress,
      toTokenAmountFormat(integrationnet_reward_amount * integrationnetNodeOperatorUpdate.operatorInQueue.daysInQueue),
      integrationnetNodeOperatorUpdate.operatorInQueue.daysInQueue
    )

    val updatedDataSourcesF: F[Set[DataSources]] = currentCalculatedState.get(integrationnetNodeOperatorUpdate.address) match {
      case None =>
        logger.info(s"Could not find any DataSource to the address ${integrationnetNodeOperatorUpdate.address}, creating a new one").as(
          Set[DataSources](newIntegrationnetDataSource)
        )
      case Some(dataSources) =>
        dataSources.collectFirst {
          case ex: IntegrationnetNodeOperatorDataSource => ex
        }.fold(
          logger.info(s"Could not find IntegrationnetNodeOperatorDataSource to address ${integrationnetNodeOperatorUpdate.address}, creating a new one").as(
            dataSources + newIntegrationnetDataSource
          )
        ) { existing =>
          updateIntegrationnetOpDataSourceState(
            existing,
            integrationnetNodeOperatorUpdate,
            currentEpochProgress,
            dataSources,
            logger
          )
        }
    }

    updatedDataSourcesF.map(updatedDataSources => currentCalculatedState + (integrationnetNodeOperatorUpdate.address -> updatedDataSources))
  }
}
