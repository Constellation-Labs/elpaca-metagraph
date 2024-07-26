package org.elpaca_metagraph.shared_data.combiners

import cats.effect.Async
import cats.syntax.all._
import org.elpaca_metagraph.shared_data.Utils.toTokenAmountFormat
import org.elpaca_metagraph.shared_data.types.DataUpdates.IntegrationnetNodeOperatorUpdate
import org.elpaca_metagraph.shared_data.types.IntegrationnetOperators._
import org.elpaca_metagraph.shared_data.types.States._
import org.tessellation.schema.address.Address
import org.tessellation.schema.epoch.EpochProgress
import org.typelevel.log4cats.Logger

object IntegrationnetOperatorsCombiner {

  private val integrationnetRewardAmount: Long = 1L

  private def calculateDaysInQueue(existing: IntegrationnetNodeOperatorDataSourceAddress, update: IntegrationnetNodeOperatorUpdate): Long =
    update.operatorInQueue.daysInQueue - existing.daysInQueue

  private def updateIntegrationnetOpDataSourceState[F[_] : Async : Logger](
    existing     : IntegrationnetNodeOperatorDataSourceAddress,
    update       : IntegrationnetNodeOperatorUpdate,
    dataSource   : IntegrationnetNodeOperatorDataSource,
    epochProgress: EpochProgress
  ): F[Map[Address, IntegrationnetNodeOperatorDataSourceAddress]] = {

    val daysInQueue = calculateDaysInQueue(existing, update)

    if (daysInQueue <= 0L) {
      dataSource.addresses.pure[F]
    } else {
      val updatedAddress = IntegrationnetNodeOperatorDataSourceAddress(
        epochProgress,
        toTokenAmountFormat(integrationnetRewardAmount),
        update.operatorInQueue.daysInQueue
      )

      Logger[F].info(s"Updated IntegrationnetNodeOperatorDataSource for address ${update.address}")
        .as(dataSource.addresses.updated(update.address, updatedAddress))
    }
  }

  private def getIntegrationnetOperatorsUpdatedAddresses[F[_] : Async : Logger](
    state        : Map[DataSourceType, DataSource],
    update       : IntegrationnetNodeOperatorUpdate,
    epochProgress: EpochProgress,
  ): F[Map[Address, IntegrationnetNodeOperatorDataSourceAddress]] = {

    val integrationnetNodeOperatorDataSourceAddress = IntegrationnetNodeOperatorDataSourceAddress(
      epochProgress,
      toTokenAmountFormat(integrationnetRewardAmount),
      update.operatorInQueue.daysInQueue
    )

    state.get(DataSourceType.IntegrationnetNodeOperator)
      .fold(Map(update.address -> integrationnetNodeOperatorDataSourceAddress).pure[F]) {
        case integrationnetDataSource: IntegrationnetNodeOperatorDataSource =>
          integrationnetDataSource.addresses
            .get(update.address)
            .fold(integrationnetDataSource.addresses.updated(update.address, integrationnetNodeOperatorDataSourceAddress).pure[F]) { existing =>
              updateIntegrationnetOpDataSourceState(existing, update, integrationnetDataSource, epochProgress)
            }
        case _ => new IllegalStateException("DataSource is not from type IntegrationnetNodeOperatorDataSource").raiseError[F, Map[Address, IntegrationnetNodeOperatorDataSourceAddress]]
      }
  }

  def updateStateIntegrationnetOperatorsResponse[F[_] : Async : Logger](
    currentState : Map[DataSourceType, DataSource],
    epochProgress: EpochProgress,
    update       : IntegrationnetNodeOperatorUpdate
  ): F[IntegrationnetNodeOperatorDataSource] =
    getIntegrationnetOperatorsUpdatedAddresses(currentState, update, epochProgress).map { updatedAddresses =>
      IntegrationnetNodeOperatorDataSource(updatedAddresses)
    }
}
