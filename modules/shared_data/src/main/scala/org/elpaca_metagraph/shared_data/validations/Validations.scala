package org.elpaca_metagraph.shared_data.validations

import cats.syntax.all._
import org.elpaca_metagraph.shared_data.app.ApplicationConfig
import org.elpaca_metagraph.shared_data.types.DataUpdates.{FreshWalletUpdate, IntegrationnetNodeOperatorUpdate, StreakUpdate}
import org.elpaca_metagraph.shared_data.types.States.StreakDataSource
import org.elpaca_metagraph.shared_data.validations.TypeValidators._
import io.constellationnetwork.currency.dataApplication.dataApplication.DataApplicationValidationErrorOr
import io.constellationnetwork.schema.epoch.EpochProgress
import io.constellationnetwork.security.signature.Signed

object Validations {
  def integrationnetNodeOperatorsValidationsL1(
    integrationnetNodeOpUpdate: IntegrationnetNodeOperatorUpdate
  ): DataApplicationValidationErrorOr[Unit] =
    validateIfIntegrationnetOperatorHave250KDAG(integrationnetNodeOpUpdate)

  def streakValidationsL0(
    streakUpdate        : Signed[StreakUpdate],
    streakDataSource    : StreakDataSource,
    appConfig           : ApplicationConfig,
    currentEpochProgress: EpochProgress
  ): DataApplicationValidationErrorOr[Unit] = {
    validateIfUpdateWasSignedByStargazer(streakUpdate.proofs, appConfig)
      .productR(validateIfAddressAlreadyRewardedInCurrentDay(streakUpdate.value, streakDataSource, currentEpochProgress))
      .productR(validateIfTokenIsValid(streakUpdate.value, streakDataSource))

  }

  def freshWalletValidationsL0(
    freshWalletUpdate: Signed[FreshWalletUpdate],
    appConfig        : ApplicationConfig,
  ): DataApplicationValidationErrorOr[Unit] = {
    validateIfUpdateWasSignedByStargazer(freshWalletUpdate.proofs, appConfig)
  }
}
