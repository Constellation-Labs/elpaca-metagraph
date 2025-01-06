package org.elpaca_metagraph.shared_data.validations

import cats.syntax.all._
import org.elpaca_metagraph.shared_data.app.ApplicationConfig
import org.elpaca_metagraph.shared_data.types.DataUpdates.{IntegrationnetNodeOperatorUpdate, StreakUpdate}
import org.elpaca_metagraph.shared_data.types.States.StreakDataSource
import org.elpaca_metagraph.shared_data.validations.TypeValidators._
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
    streakDataSource    : StreakDataSource,
    appConfig           : ApplicationConfig,
    currentEpochProgress: EpochProgress
  ): DataApplicationValidationErrorOr[Unit] = {
    validateIfUpdateWasSignedByStargazer(streakUpdate.proofs, appConfig)
      .productR(validateIfAddressAlreadyRewardedInCurrentDay(streakUpdate.value, streakDataSource, currentEpochProgress))
      .productR(validateIfTokenIsValid(streakUpdate.value, streakDataSource))

  }
}
