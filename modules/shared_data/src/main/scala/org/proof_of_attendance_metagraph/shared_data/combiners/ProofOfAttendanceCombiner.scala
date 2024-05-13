package org.proof_of_attendance_metagraph.shared_data.combiners

import org.proof_of_attendance_metagraph.shared_data.types.DataUpdates._
import org.proof_of_attendance_metagraph.shared_data.types.States._
import org.tessellation.currency.dataApplication.DataState
import org.tessellation.schema.address.Address
import org.tessellation.schema.epoch.EpochProgress
import org.tessellation.security.signature.Signed

object ProofOfAttendanceCombiner {
  private def updateStateExolixResponse(
    currentCalculatedState: Map[Address, Set[DataSources]],
    epochProgressToReward : EpochProgress,
    address               : Address
  ): Map[Address, Set[DataSources]] = {
    val exolixDataSource = ExolixDataSource(
      epochProgressToReward,
      100, // TBD
      address.value.toString()
    )
    val exolixDataSourceList = Set[DataSources](exolixDataSource)
    val updatedDataSources = currentCalculatedState
      .get(address)
      .fold(exolixDataSourceList) { dataSources =>
        dataSources.collectFirst {
            case ex: ExolixDataSource => ex
          }
          .fold(dataSources + exolixDataSource) { existing =>
            if (existing.epochProgressToReward.value.value + 1L < epochProgressToReward.value.value) {
              dataSources - existing + exolixDataSource
            } else {
              dataSources
            }
          }
      }
    currentCalculatedState + (address -> updatedDataSources)
  }

  private def updateStateSimplexResponse(
    currentCalculatedState: Map[Address, Set[DataSources]],
    epochProgressToReward : EpochProgress,
    address               : Address
  ): Map[Address, Set[DataSources]] = {
    val simplexDataSource = SimplexDataSource(
      epochProgressToReward,
      200, // TBD
      address.value.toString()
    )
    val simplexDataSourceList = Set[DataSources](simplexDataSource)
    val updatedDataSources = currentCalculatedState
      .get(address)
      .fold(simplexDataSourceList) { dataSources =>
        dataSources.collectFirst {
            case ex: SimplexDataSource => ex
          }
          .fold(dataSources + simplexDataSource) { existing =>
            if (existing.epochProgressToReward.value.value + 2L < epochProgressToReward.value.value) {
              dataSources - existing + simplexDataSource
            } else {
              dataSources
            }
          }
      }
    currentCalculatedState + (address -> updatedDataSources)
  }

  private def updateTwitterResponse(
    currentCalculatedState: Map[Address, Set[DataSources]],
    epochProgressToReward : EpochProgress,
    address               : Address
  ): Map[Address, Set[DataSources]] = {
    val twitterDataSource = TwitterDataSource(
      epochProgressToReward,
      300, // TBD
      address.value.toString()
    )
    val twitterDataSourceList = Set[DataSources](twitterDataSource)
    val updatedDataSources = currentCalculatedState
      .get(address)
      .fold(twitterDataSourceList) { dataSources =>
        dataSources.collectFirst {
            case ex: TwitterDataSource => ex
          }
          .fold(dataSources + twitterDataSource) { existing =>
            if (existing.epochProgressToReward.value.value + 3L < epochProgressToReward.value.value) {
              dataSources - existing + twitterDataSource
            } else {
              dataSources
            }
          }
      }
    currentCalculatedState + (address -> updatedDataSources)
  }

  private def updateIntegrationnetOperatorsResponse(
    currentCalculatedState: Map[Address, Set[DataSources]],
    epochProgressToReward : EpochProgress,
    address               : Address
  ): Map[Address, Set[DataSources]] = {
    val integrationnetNodeOperatorDataSource = IntegrationnetNodeOperatorLineDataSource(
      epochProgressToReward,
      400, // TBD
      address.value.toString()
    )
    val integrationnetNodeOperatorDataSourceList = Set[DataSources](integrationnetNodeOperatorDataSource)
    val updatedDataSources = currentCalculatedState
      .get(address)
      .fold(integrationnetNodeOperatorDataSourceList) { dataSources =>
        dataSources.collectFirst {
            case ex: IntegrationnetNodeOperatorLineDataSource => ex
          }
          .fold(dataSources + integrationnetNodeOperatorDataSource) { existing =>
            if (existing.epochProgressToReward.value.value + 4L < epochProgressToReward.value.value) {
              dataSources - existing + integrationnetNodeOperatorDataSource
            } else {
              dataSources
            }
          }
      }
    currentCalculatedState + (address -> updatedDataSources)
  }

  def combineProofOfAttendance(
    oldState            : DataState[ProofOfAttendanceOnChainState, ProofOfAttendanceCalculatedState],
    currentEpochProgress: EpochProgress,
    update              : Signed[ProofOfAttendanceUpdate]
  ): DataState[ProofOfAttendanceOnChainState, ProofOfAttendanceCalculatedState] = {
    val updatedCalculatedState = update.value match {
      case update: ExolixUpdate =>
        updateStateExolixResponse(
          oldState.calculated.addresses,
          currentEpochProgress,
          update.address
        )
      case update: SimplexUpdate =>
        updateStateSimplexResponse(
          oldState.calculated.addresses,
          currentEpochProgress,
          update.address
        )
      case update: TwitterUpdate =>
        updateTwitterResponse(
          oldState.calculated.addresses,
          currentEpochProgress,
          update.address
        )
      case update: IntegrationnetNodeOperatorUpdate =>
        updateIntegrationnetOperatorsResponse(
          oldState.calculated.addresses,
          currentEpochProgress,
          update.address
        )
    }

    val updates: List[ProofOfAttendanceUpdate] = update.value :: oldState.onChain.updates
    DataState(
      ProofOfAttendanceOnChainState(updates),
      ProofOfAttendanceCalculatedState(updatedCalculatedState)
    )
  }
}