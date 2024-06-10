package org.elpaca_metagraph.shared_data

import cats.effect.Async
import cats.syntax.all._
import org.elpaca_metagraph.shared_data.combiners.Combiner.combineElpacaUpdate
import org.elpaca_metagraph.shared_data.combiners.FreshWalletCombiner.cleanFreshWalletsAlreadyRewarded
import org.elpaca_metagraph.shared_data.combiners.WalletCreationHoldingDAGCombiner.cleanWalletCreationHoldingDAGAlreadyRewardedWallets
import org.elpaca_metagraph.shared_data.types.DataUpdates.{ElpacaUpdate, IntegrationnetNodeOperatorUpdate}
import org.elpaca_metagraph.shared_data.types.States.{ElpacaCalculatedState, ElpacaOnChainState}
import org.elpaca_metagraph.shared_data.validations.Errors.valid
import org.elpaca_metagraph.shared_data.validations.Validations.integrationnetNodeOperatorsValidationsL1
import org.tessellation.currency.dataApplication.dataApplication.DataApplicationValidationErrorOr
import org.tessellation.currency.dataApplication.{DataState, L0NodeContext}
import org.tessellation.ext.cats.syntax.next.catsSyntaxNext
import org.tessellation.schema.epoch.EpochProgress
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

  def combine[F[_] : Async](
    oldState: DataState[ElpacaOnChainState, ElpacaCalculatedState],
    updates : List[Signed[ElpacaUpdate]]
  )(implicit context: L0NodeContext[F]): F[DataState[ElpacaOnChainState, ElpacaCalculatedState]] = {
    val newState = DataState(ElpacaOnChainState(List.empty), ElpacaCalculatedState(oldState.calculated.dataSources))
    for {
      epochProgress <- context.getLastCurrencySnapshot.flatMap {
        case Some(value) => value.epochProgress.next.pure[F]
        case None =>
          val message = "Could not get the epochProgress from currency snapshot. lastCurrencySnapshot not found"
          logger.error(message) >> new Exception(message).raiseError[F, EpochProgress]
      }
      response <- if (updates.isEmpty) {
        logger.info("Snapshot without any updates, updating the state to empty updates").as(
          newState
        )
      } else {
        for {
          _ <- logger.info(s"Incoming updates: ${updates.length}")
          combined <- updates.foldLeftM(newState) { (acc, signedUpdate) =>
            combineElpacaUpdate(
              acc,
              epochProgress,
              signedUpdate
            )
          }
        } yield combined
      }

      cleanedWalletCreationHoldingDAG = cleanWalletCreationHoldingDAGAlreadyRewardedWallets(response.calculated.dataSources, epochProgress)
      cleanedResponse = cleanFreshWalletsAlreadyRewarded(cleanedWalletCreationHoldingDAG, epochProgress)
    } yield DataState(
      response.onChain,
      ElpacaCalculatedState(cleanedResponse)
    )
  }
}