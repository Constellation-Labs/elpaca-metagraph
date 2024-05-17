package org.proof_of_attendance_metagraph.shared_data.combiners

import cats.effect.Async
import org.proof_of_attendance_metagraph.shared_data.combiners.ProofOfAttendanceCombiner.epoch_progress_1_day
import org.proof_of_attendance_metagraph.shared_data.types.DataUpdates._
import org.proof_of_attendance_metagraph.shared_data.types.States._
import org.tessellation.schema.address.Address
import org.tessellation.schema.epoch.EpochProgress

//TODO: Implement the correct flow
object IntegrationnetOperatorsCombiner {
  def updateStateIntegrationnetOperatorsResponse[F[_] : Async](
    currentCalculatedState          : Map[Address, Set[DataSources]],
    currentEpochProgress            : EpochProgress,
    integrationnetNodeOperatorUpdate: IntegrationnetNodeOperatorUpdate
  ): F[Map[Address, Set[DataSources]]] = Async[F].delay {
    val integrationnetNodeOperatorDataSource = IntegrationnetNodeOperatorLineDataSource(
      currentEpochProgress,
      400, // TBD
      integrationnetNodeOperatorUpdate.address.value.value
    )
    val integrationnetNodeOperatorDataSourceList = Set[DataSources](integrationnetNodeOperatorDataSource)
    val updatedDataSources = currentCalculatedState
      .get(integrationnetNodeOperatorUpdate.address)
      .fold(integrationnetNodeOperatorDataSourceList) { dataSources =>
        dataSources.collectFirst {
            case ex: IntegrationnetNodeOperatorLineDataSource => ex
          }
          .fold(dataSources + integrationnetNodeOperatorDataSource) { existing =>
            if (existing.epochProgressToReward.value.value + epoch_progress_1_day < currentEpochProgress.value.value) {
              dataSources - existing + integrationnetNodeOperatorDataSource
            } else {
              dataSources
            }
          }
      }
    currentCalculatedState + (integrationnetNodeOperatorUpdate.address -> updatedDataSources)
  }
}
