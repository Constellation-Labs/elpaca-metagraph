package org.elpaca_metagraph.shared_data.validations

import cats.data.NonEmptySet
import org.elpaca_metagraph.shared_data.Utils._
import org.elpaca_metagraph.shared_data.app.ApplicationConfig
import org.elpaca_metagraph.shared_data.types.DataUpdates._
import org.elpaca_metagraph.shared_data.types.States.StreakDataSource
import org.elpaca_metagraph.shared_data.types.Streak.StreakDataSourceAddress
import org.elpaca_metagraph.shared_data.validations.Errors._
import org.tessellation.currency.dataApplication.dataApplication.DataApplicationValidationErrorOr
import org.tessellation.schema.epoch.EpochProgress
import org.tessellation.security.signature.signature.SignatureProof

object TypeValidators {
  def validateIfIntegrationnetOperatorHave250KDAG(
    integrationnetOpUpdate: IntegrationnetNodeOperatorUpdate
  ): DataApplicationValidationErrorOr[Unit] = {
    val balance = integrationnetOpUpdate.operatorInQueue.walletBalance
    val dag_collateral = toTokenFormat(250000)
    IntegrationnetNodeOperatorBalanceLessThan250K.unlessA(balance >= dag_collateral)
  }

  def validateIfUpdateWasSignedByStargazer(
    proofs           : NonEmptySet[SignatureProof],
    applicationConfig: ApplicationConfig
  ): DataApplicationValidationErrorOr[Unit] =
    StreakUpdateNotSignedByStargazer.unlessA(walletSignedTheMessage(applicationConfig.streak.stargazerPublicKey, proofs))

  def validateIfAddressAlreadyRewardedInCurrentDay(
    streakUpdate        : StreakUpdate,
    streakDataSource    : StreakDataSource,
    currentEpochProgress: EpochProgress
  ): DataApplicationValidationErrorOr[Unit] = {
    val streakDataSourceAddress = streakDataSource.existingWallets.getOrElse(streakUpdate.address, StreakDataSourceAddress.empty)
    StreakAddressAlreadyRewarded.unlessA(isNewDay(streakDataSourceAddress.epochProgressToReward, currentEpochProgress))
  }

  def validateIfTokenIsValid(
    streakUpdate     : StreakUpdate,
    streakDataSource : StreakDataSource
  ): DataApplicationValidationErrorOr[Unit] = {
    val streakDataSourceAddress = streakDataSource.existingWallets.getOrElse(streakUpdate.address, StreakDataSourceAddress.empty)
    if (streakDataSourceAddress.nextToken.isEmpty || streakDataSourceAddress.nextToken.contains("")) {
      valid
    } else {
      streakUpdate.token.map { token =>
          InvalidToken.whenA(token != streakDataSourceAddress.nextToken.get)
        }
        .getOrElse(MissingToken.invalid)
    }
  }
}
