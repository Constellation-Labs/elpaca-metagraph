package org.elpaca_metagraph.shared_data.validations

import cats.syntax.all._
import org.elpaca_metagraph.shared_data.app.ApplicationConfig
import org.elpaca_metagraph.shared_data.types.DataUpdates.{IntegrationnetNodeOperatorUpdate, StreakUpdate}
import org.elpaca_metagraph.shared_data.types.States.DataSourceType.Streak
import org.elpaca_metagraph.shared_data.types.States.{ElpacaCalculatedState, ElpacaOnChainState, StreakDataSource}
import org.elpaca_metagraph.shared_data.validations.TypeValidators.{validateIfAddressAlreadyRewardedInCurrentDay, validateIfIntegrationnetOperatorHave250KDAG, validateIfUpdateWasSignedByStargazer}
import org.tessellation.currency.dataApplication.DataState
import org.tessellation.currency.dataApplication.dataApplication.DataApplicationValidationErrorOr
import org.tessellation.schema.epoch.EpochProgress
import org.tessellation.security.signature.Signed

object Validations {
  def integrationnetNodeOperatorsValidationsL1(
    integrationnetNodeOpUpdate: IntegrationnetNodeOperatorUpdate
  ): DataApplicationValidationErrorOr[Unit] =
    validateIfIntegrationnetOperatorHave250KDAG(integrationnetNodeOpUpdate)

  def streakValidationsL0(
    streakUpdate        : Signed[StreakUpdate],
    oldState            : DataState[ElpacaOnChainState, ElpacaCalculatedState],
    appConfig           : ApplicationConfig,
    currentEpochProgress: EpochProgress
  ): DataApplicationValidationErrorOr[Unit] = {
    val streakDataSource = oldState.calculated.dataSources
      .get(Streak)
      .collect { case ds: StreakDataSource => ds }
      .getOrElse(StreakDataSource(Map.empty))

    validateIfUpdateWasSignedByStargazer(streakUpdate.proofs, appConfig).productR(
      validateIfAddressAlreadyRewardedInCurrentDay(streakUpdate.value, streakDataSource, currentEpochProgress)
    )

  }
}
