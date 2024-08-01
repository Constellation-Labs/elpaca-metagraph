package org.elpaca_metagraph.l0.rewards

import cats.effect.Async
import cats.syntax.all._
import eu.timepit.refined.types.numeric.PosLong
import org.elpaca_metagraph.shared_data.Utils.PosLongOps
import org.elpaca_metagraph.shared_data.types.States.DataSourceType._
import org.elpaca_metagraph.shared_data.types.States._
import org.tessellation.currency.dataApplication.DataCalculatedState
import org.tessellation.currency.schema.currency.{CurrencyIncrementalSnapshot, CurrencySnapshotStateProof}
import org.tessellation.ext.cats.syntax.next.catsSyntaxNext
import org.tessellation.node.shared.domain.rewards.Rewards
import org.tessellation.node.shared.infrastructure.consensus.trigger.{ConsensusTrigger, EventTrigger, TimeTrigger}
import org.tessellation.node.shared.snapshot.currency.CurrencySnapshotEvent
import org.tessellation.schema.address.Address
import org.tessellation.schema.balance.{Amount, Balance}
import org.tessellation.schema.epoch.EpochProgress
import org.tessellation.schema.transaction.{RewardTransaction, Transaction, TransactionAmount}
import org.tessellation.security.signature.Signed
import org.typelevel.log4cats.slf4j.Slf4jLogger

import scala.collection.immutable.{Map, SortedMap, SortedSet}

object ElpacaRewards {
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
      val logger = Slf4jLogger.getLoggerFromName[F]("ElpacaRewards")

      def noRewards: F[SortedSet[RewardTransaction]] = SortedSet.empty[RewardTransaction].pure[F]

      def getAddressAndAmounts(
        state               : ElpacaCalculatedState,
        currentEpochProgress: EpochProgress,
        dataSourceType      : DataSourceType
      ): Map[Address, Amount] = {
        state.dataSources.get(dataSourceType).fold(Map.empty[Address, Amount]) {
          case dataSource: ExolixDataSource =>
            dataSource.addresses.collect {
              case (address, ds) if ds.epochProgressToReward === currentEpochProgress => address -> ds.amountToReward
            }
          case dataSource: SimplexDataSource =>
            dataSource.addresses.collect {
              case (address, ds) if ds.epochProgressToReward === currentEpochProgress => address -> ds.amountToReward
            }
          case dataSource: IntegrationnetNodeOperatorDataSource =>
            dataSource.addresses.collect {
              case (address, ds) if ds.epochProgressToReward === currentEpochProgress => address -> ds.amountToReward
            }
          case dataSource: WalletCreationHoldingDAGDataSource =>
            dataSource.addressesToReward.collect {
              case (address, ds) if ds.epochProgressToReward.contains(currentEpochProgress) => address -> ds.amountToReward
            }
          case dataSource: FreshWalletDataSource =>
            dataSource.addressesToReward.collect {
              case (address, ds) if ds.epochProgressToReward === currentEpochProgress => address -> ds.amountToReward
            }

          case dataSource: InflowTransactionsDataSource =>
            dataSource.existingWallets.foldLeft(Map.empty[Address, Amount]) { (acc, dsInfo) =>
              val (_, ds) = dsInfo
              ds.addressesToReward.collect {
                case inflowAddressInfo if inflowAddressInfo.epochProgressToReward === currentEpochProgress =>
                  inflowAddressInfo.addressToReward -> inflowAddressInfo.amountToReward
              }.foldLeft(acc) { case (innerAcc, (addressToReward, rewardAmount)) =>
                innerAcc.updated(addressToReward, innerAcc.getOrElse(addressToReward, Amount.empty).plus(rewardAmount).getOrElse(Amount.empty))
              }
            }

          case dataSource: OutflowTransactionsDataSource =>
            dataSource.existingWallets.foldLeft(Map.empty[Address, Amount]) { (acc, dsInfo) =>
              val (_, ds) = dsInfo
              ds.addressesToReward.collect {
                case outflowAddressInfo if outflowAddressInfo.epochProgressToReward === currentEpochProgress =>
                  outflowAddressInfo.addressToReward -> outflowAddressInfo.amountToReward
              }.foldLeft(acc) { case (innerAcc, (addressToReward, rewardAmount)) =>
                innerAcc.updated(addressToReward, innerAcc.getOrElse(addressToReward, Amount.empty).plus(rewardAmount).getOrElse(Amount.empty))
              }
            }

          case dataSource: XDataSource =>
            dataSource.existingWallets.foldLeft(Map.empty[Address, Amount]) { (acc, dsInfo) =>
              val (rewardAddress, ds) = dsInfo
              ds.addressRewards.collect {
                case (_, addressInfo) if addressInfo.epochProgressToReward === currentEpochProgress =>
                  rewardAddress -> addressInfo.amountToReward
              }.foldLeft(acc) { case (innerAcc, (addressToReward, rewardAmount)) =>
                innerAcc.updated(addressToReward, innerAcc.getOrElse(addressToReward, Amount.empty).plus(rewardAmount).getOrElse(Amount.empty))
              }
            }

          case _ => Map.empty[Address, Amount]
        }
      }

      def buildRewards(
        proofOfAttendanceCalculatedState: ElpacaCalculatedState,
        currentEpochProgress            : EpochProgress
      ): F[SortedSet[RewardTransaction]] = for {
        _ <- logger.info("Starting to build the rewards")
        combinedAddressesAndAmounts = Seq(Exolix, Simplex, IntegrationnetNodeOperator, WalletCreationHoldingDAG, FreshWallet, InflowTransactions, OutflowTransactions, X)
          .flatMap(getAddressAndAmounts(proofOfAttendanceCalculatedState, currentEpochProgress, _))
          .groupBy(_._1)
          .view
          .mapValues(_.map(_._2.value.value).sum)
          .toMap

        transactions <- combinedAddressesAndAmounts.foldLeft(SortedSet.empty[RewardTransaction].pure[F]) {
          (rewardTransactionsF, currentAddressInfo) =>
            rewardTransactionsF.flatMap { rewardTransactions =>
              val (address, amountToReward) = currentAddressInfo
              if (amountToReward == 0) rewardTransactions.pure
              else {
                val rewardTransaction = (address, amountToReward.toPosLongUnsafe).toRewardTransaction
                logger.info(s"Address $address will be rewarded with $amountToReward in ${currentEpochProgress.show}")
                  .as(rewardTransactions + rewardTransaction)
              }
            }
        }
      } yield transactions

      trigger match {
        case EventTrigger => noRewards
        case TimeTrigger =>
          val currentEpochProgress: EpochProgress = lastArtifact.epochProgress.next
          maybeCalculatedState.fold(noRewards) {
            case proofOfAttendanceCalculatedState: ElpacaCalculatedState =>
              buildRewards(proofOfAttendanceCalculatedState, currentEpochProgress)
            case _ => logger.error("Invalid calculated state class") >> noRewards
          }
      }
    }
}
