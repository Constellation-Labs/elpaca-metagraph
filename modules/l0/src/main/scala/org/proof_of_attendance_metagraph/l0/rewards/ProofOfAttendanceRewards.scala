package org.proof_of_attendance_metagraph.l0.rewards

import cats.effect.Async
import cats.syntax.all._
import eu.timepit.refined.types.numeric.PosLong
import org.proof_of_attendance_metagraph.shared_data.Utils.PosLongOps
import org.proof_of_attendance_metagraph.shared_data.types.States.ProofOfAttendanceCalculatedState
import org.tessellation.currency.dataApplication.DataCalculatedState
import org.tessellation.currency.schema.currency.{CurrencyIncrementalSnapshot, CurrencySnapshotStateProof}
import org.tessellation.ext.cats.syntax.next.catsSyntaxNext
import org.tessellation.node.shared.domain.rewards.Rewards
import org.tessellation.node.shared.infrastructure.consensus.trigger.{ConsensusTrigger, EventTrigger, TimeTrigger}
import org.tessellation.node.shared.snapshot.currency.CurrencySnapshotEvent
import org.tessellation.schema.address.Address
import org.tessellation.schema.balance.Balance
import org.tessellation.schema.epoch.EpochProgress
import org.tessellation.schema.transaction.{RewardTransaction, Transaction, TransactionAmount}
import org.tessellation.security.signature.Signed
import org.typelevel.log4cats.slf4j.Slf4jLogger

import scala.collection.immutable.{SortedMap, SortedSet}

object ProofOfAttendanceRewards {
  implicit class RewardTransactionOps(tuple: (Address, PosLong)) {
    def toRewardTransaction: RewardTransaction = {
      val (address, amount) = tuple
      RewardTransaction(address, TransactionAmount(amount))
    }
  }

  def make[F[_] : Async](): Rewards[F, CurrencySnapshotStateProof, CurrencyIncrementalSnapshot, CurrencySnapshotEvent] =
    (
      lastArtifact        : Signed[CurrencyIncrementalSnapshot],
      _                   : SortedMap[Address, Balance],
      _                   : SortedSet[Signed[Transaction]],
      trigger             : ConsensusTrigger,
      _                   : Set[CurrencySnapshotEvent],
      maybeCalculatedState: Option[DataCalculatedState]
    ) => {
      val logger = Slf4jLogger.getLoggerFromName[F]("ProofOfAttendanceRewards")


      def noRewards: F[SortedSet[RewardTransaction]] = SortedSet.empty[RewardTransaction].pure[F]

      def distributeCurrentEpochProgressRewards(
        state               : ProofOfAttendanceCalculatedState,
        currentEpochProgress: EpochProgress
      ): F[SortedSet[RewardTransaction]] = {
        val initialRewards = SortedSet.empty[RewardTransaction].pure[F]

        state.addresses.foldLeft(initialRewards) { (rewardTransactionsF, currentAddressInfo) =>
          rewardTransactionsF.flatMap { rewardTransactions =>
            val (address, dataSources) = currentAddressInfo
            val amountToReward = dataSources
              .filter(dataSource => dataSource.epochProgressToReward.value == currentEpochProgress.value)
              .map(_.amountToReward)
              .sum

            if (amountToReward == 0) {
              logger.info(s"Address $address doesn't have rewards at epoch progress: ${currentEpochProgress.value.value}")
                .as(rewardTransactions)
            } else {
              val rewardTransaction = (address, amountToReward.toPosLongUnsafe).toRewardTransaction
              logger.info(s"Address $address will be rewarded with $amountToReward in this snapshot")
                .as(rewardTransactions + rewardTransaction)
            }
          }
        }
      }

      trigger match {
        case EventTrigger => noRewards
        case TimeTrigger =>
          val currentEpochProgress: EpochProgress = lastArtifact.epochProgress.next
          maybeCalculatedState match {
            case None => noRewards
            case Some(dataCalculatedState) =>
              dataCalculatedState match {
                case proofOfAttendanceCalculatedState: ProofOfAttendanceCalculatedState =>
                  distributeCurrentEpochProgressRewards(proofOfAttendanceCalculatedState, currentEpochProgress)
                case _ => logger.error("Invalid calculated state class") >> noRewards
              }
          }
      }
    }
}