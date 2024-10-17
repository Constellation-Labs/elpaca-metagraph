package org.elpaca_metagraph.shared_data

import cats.data.NonEmptyList
import cats.effect.Async
import cats.syntax.all._
import org.elpaca_metagraph.shared_data.Utils.getCurrentEpochProgress
import org.elpaca_metagraph.shared_data.app.ApplicationConfig
import org.elpaca_metagraph.shared_data.combiners.Combiner.combineElpacaUpdate
import org.elpaca_metagraph.shared_data.combiners.FreshWalletCombiner.cleanFreshWalletsAlreadyRewarded
import org.elpaca_metagraph.shared_data.combiners.InflowTransactionsCombiner.cleanInflowTransactionsRewarded
import org.elpaca_metagraph.shared_data.combiners.OutflowTransactionsCombiner.cleanOutflowTransactionsRewarded
import org.elpaca_metagraph.shared_data.combiners.WalletCreationHoldingDAGCombiner.cleanWalletCreationHoldingDAGAlreadyRewardedWallets
import org.elpaca_metagraph.shared_data.combiners.XCombiner.updateRewardsOlderThanOneDay
import org.elpaca_metagraph.shared_data.types.DataUpdates.{ElpacaUpdate, IntegrationnetNodeOperatorUpdate, StreakUpdate}
import org.elpaca_metagraph.shared_data.types.States.{ElpacaCalculatedState, ElpacaOnChainState}
import org.elpaca_metagraph.shared_data.validations.Errors.valid
import org.elpaca_metagraph.shared_data.validations.Validations.{integrationnetNodeOperatorsValidationsL1, streakValidationsL0}
import org.tessellation.currency.dataApplication.dataApplication.DataApplicationValidationErrorOr
import org.tessellation.currency.dataApplication.{DataState, L0NodeContext}
import org.tessellation.security.signature.Signed
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

object LifecycleSharedFunctions {
  def logger[F[_] : Async]: SelfAwareStructuredLogger[F] = Slf4jLogger.getLoggerFromName[F]("LifecycleSharedFunctions")

  def validateUpdate[F[_] : Async](
    update: ElpacaUpdate
  ): F[DataApplicationValidationErrorOr[Unit]] =
    update match {
      case integrationnetOpUpdate: IntegrationnetNodeOperatorUpdate =>
        Async[F].delay {
          integrationnetNodeOperatorsValidationsL1(integrationnetOpUpdate)
        }
      case _ => valid.pure[F]
    }

  def validateData[F[_] : Async](
    updates  : NonEmptyList[Signed[ElpacaUpdate]],
    oldState : DataState[ElpacaOnChainState, ElpacaCalculatedState],
    appConfig: ApplicationConfig
  )(implicit context: L0NodeContext[F]): F[DataApplicationValidationErrorOr[Unit]] = {
    updates.traverse { update =>
      update.value match {
        case streakUpdate: StreakUpdate =>
          for {
            epochProgress <- getCurrentEpochProgress
          } yield streakValidationsL0(Signed(streakUpdate, update.proofs), oldState, appConfig, epochProgress)
        case _ => valid.pure[F]
      }
    }.map(_.reduce)

  }

  def combine[F[_] : Async](
    oldState : DataState[ElpacaOnChainState, ElpacaCalculatedState],
    updates  : List[Signed[ElpacaUpdate]],
    appConfig: ApplicationConfig
  )(implicit context: L0NodeContext[F]): F[DataState[ElpacaOnChainState, ElpacaCalculatedState]] = {
    val initialNewState = DataState(
      ElpacaOnChainState(List.empty),
      ElpacaCalculatedState(oldState.calculated.dataSources)
    )

    for {
      epochProgress <- getCurrentEpochProgress
      combinedState <-
        if (updates.isEmpty) {
          logger.info("Snapshot without any updates, updating the state to empty updates")
            .as(initialNewState)
        } else {
          logger.info(s"Incoming updates: ${updates.length}") >>
            updates.foldLeftM(initialNewState) { (acc, signedUpdate) =>
              combineElpacaUpdate(acc, epochProgress, signedUpdate, appConfig)
            }
        }

      cleanedWallets = cleanWalletCreationHoldingDAGAlreadyRewardedWallets(
        combinedState.calculated.dataSources, epochProgress
      )
      cleanedFreshWallets = cleanFreshWalletsAlreadyRewarded(cleanedWallets, epochProgress)
      cleanedInflow = cleanInflowTransactionsRewarded(cleanedFreshWallets, epochProgress)
      cleanedOutflow = cleanOutflowTransactionsRewarded(cleanedInflow, epochProgress)
      finalRewards = updateRewardsOlderThanOneDay(cleanedOutflow, epochProgress)

      updatedCalculatedState = ElpacaCalculatedState(finalRewards)
    } yield DataState(
      combinedState.onChain,
      updatedCalculatedState
    )
  }
}