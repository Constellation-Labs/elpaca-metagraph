package org.proof_of_attendance_metagraph.shared_data.combiners

import cats.effect.Async
import org.proof_of_attendance_metagraph.shared_data.combiners.ProofOfAttendanceCombiner.epoch_progress_1_day
import org.proof_of_attendance_metagraph.shared_data.types.DataUpdates._
import org.proof_of_attendance_metagraph.shared_data.types.States._
import org.tessellation.schema.address.Address
import org.tessellation.schema.epoch.EpochProgress

//TODO: Implement the correct flow
object TwitterCombiner {
  def updateStateTwitterResponse[F[_] : Async](
    currentCalculatedState: Map[Address, Set[DataSources]],
    currentEpochProgress  : EpochProgress,
    twitterUpdate         : TwitterUpdate
  ): F[Map[Address, Set[DataSources]]] = Async[F].delay {
    //    def logger: SelfAwareStructuredLogger[F] = Slf4jLogger.getLoggerFromName[F]("TwitterCombiner")

    val twitterDataSource = TwitterDataSource(
      currentEpochProgress,
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
            if (existing.epochProgressToReward.value.value + epoch_progress_1_day < currentEpochProgress.value.value) {
              dataSources - existing + twitterDataSource
            } else {
              dataSources
            }
          }
      }
    currentCalculatedState + (twitterUpdate.address -> updatedDataSources)
  }
}
