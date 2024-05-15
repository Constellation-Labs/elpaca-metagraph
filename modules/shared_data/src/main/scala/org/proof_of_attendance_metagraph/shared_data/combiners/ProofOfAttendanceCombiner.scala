package org.proof_of_attendance_metagraph.shared_data.combiners

import org.proof_of_attendance_metagraph.shared_data.Utils.toTokenAmountFormat
import org.proof_of_attendance_metagraph.shared_data.types.DataUpdates._
import org.proof_of_attendance_metagraph.shared_data.types.States._
import org.tessellation.currency.dataApplication.DataState
import org.tessellation.ext.cats.syntax.next.catsSyntaxNext
import org.tessellation.schema.address.Address
import org.tessellation.schema.epoch.EpochProgress
import org.tessellation.security.signature.Signed

object ProofOfAttendanceCombiner {
  private val epoch_progress_1_day: Long = 1440L
  private val exolix_reward_amount: Long = 35L

  private def updateStateExolixResponse(
    currentCalculatedState: Map[Address, Set[DataSources]],
    epochProgressToReward : EpochProgress,
    exolixUpdate          : ExolixUpdate
  ): Map[Address, Set[DataSources]] = {
    val exolixDataSource = ExolixDataSource(
      epochProgressToReward,
      toTokenAmountFormat(exolix_reward_amount),
      exolixUpdate.exolixTransaction
    )
    val exolixDataSourceList = Set[DataSources](exolixDataSource)
    val updatedDataSources = currentCalculatedState
      .get(exolixUpdate.address)
      .fold(exolixDataSourceList) { dataSources =>
        dataSources.collectFirst {
            case ex: ExolixDataSource => ex
          }
          .fold(dataSources + exolixDataSource) { existing =>
            if (existing.epochProgressToReward.value.value + epoch_progress_1_day < epochProgressToReward.value.value) {
              dataSources - existing + exolixDataSource
            } else {
              dataSources
            }
          }
      }
    currentCalculatedState + (exolixUpdate.address -> updatedDataSources)
  }

  private def updateStateSimplexResponse(
    currentCalculatedState: Map[Address, Set[DataSources]],
    epochProgressToReward : EpochProgress,
    simplexUpdate         : SimplexUpdate
  ): Map[Address, Set[DataSources]] = {
    val simplexDataSource = SimplexDataSource(
      epochProgressToReward,
      200, // TBD
      simplexUpdate.address.value.value
    )
    val simplexDataSourceList = Set[DataSources](simplexDataSource)
    val updatedDataSources = currentCalculatedState
      .get(simplexUpdate.address)
      .fold(simplexDataSourceList) { dataSources =>
        dataSources.collectFirst {
            case ex: SimplexDataSource => ex
          }
          .fold(dataSources + simplexDataSource) { existing =>
            if (existing.epochProgressToReward.value.value + epoch_progress_1_day < epochProgressToReward.value.value) {
              dataSources - existing + simplexDataSource
            } else {
              dataSources
            }
          }
      }
    currentCalculatedState + (simplexUpdate.address -> updatedDataSources)
  }

  private def updateTwitterResponse(
    currentCalculatedState: Map[Address, Set[DataSources]],
    epochProgressToReward : EpochProgress,
    twitterUpdate         : TwitterUpdate
  ): Map[Address, Set[DataSources]] = {
    val twitterDataSource = TwitterDataSource(
      epochProgressToReward,
      300, // TBD
      twitterUpdate.address.value.value
    )
    val twitterDataSourceList = Set[DataSources](twitterDataSource)
    val updatedDataSources = currentCalculatedState
      .get(twitterUpdate.address)
      .fold(twitterDataSourceList) { dataSources =>
        dataSources.collectFirst {
            case ex: TwitterDataSource => ex
          }
          .fold(dataSources + twitterDataSource) { existing =>
            if (existing.epochProgressToReward.value.value + epoch_progress_1_day < epochProgressToReward.value.value) {
              dataSources - existing + twitterDataSource
            } else {
              dataSources
            }
          }
      }
    currentCalculatedState + (twitterUpdate.address -> updatedDataSources)
  }

  private def updateIntegrationnetOperatorsResponse(
    currentCalculatedState          : Map[Address, Set[DataSources]],
    epochProgressToReward           : EpochProgress,
    integrationnetNodeOperatorUpdate: IntegrationnetNodeOperatorUpdate
  ): Map[Address, Set[DataSources]] = {
    val integrationnetNodeOperatorDataSource = IntegrationnetNodeOperatorLineDataSource(
      epochProgressToReward,
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
            if (existing.epochProgressToReward.value.value + epoch_progress_1_day < epochProgressToReward.value.value) {
              dataSources - existing + integrationnetNodeOperatorDataSource
            } else {
              dataSources
            }
          }
      }
    currentCalculatedState + (integrationnetNodeOperatorUpdate.address -> updatedDataSources)
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
          currentEpochProgress.next,
          update
        )
      case update: SimplexUpdate =>
        updateStateSimplexResponse(
          oldState.calculated.addresses,
          currentEpochProgress.next,
          update
        )
      case update: TwitterUpdate =>
        updateTwitterResponse(
          oldState.calculated.addresses,
          currentEpochProgress.next,
          update
        )
      case update: IntegrationnetNodeOperatorUpdate =>
        updateIntegrationnetOperatorsResponse(
          oldState.calculated.addresses,
          currentEpochProgress.next,
          update
        )
    }

    val updates: List[ProofOfAttendanceUpdate] = update.value :: oldState.onChain.updates
    DataState(
      ProofOfAttendanceOnChainState(updates),
      ProofOfAttendanceCalculatedState(updatedCalculatedState)
    )
  }
}