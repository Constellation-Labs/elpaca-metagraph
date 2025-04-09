package org.elpaca_metagraph.shared_data.combiners

import cats.data.Validated
import cats.effect.Async
import cats.syntax.all._
import eu.timepit.refined.types.numeric.NonNegLong
import monocle.Monocle.toAppliedFocusOps
import org.elpaca_metagraph.shared_data.Utils._
import org.elpaca_metagraph.shared_data.app.ApplicationConfig
import org.elpaca_metagraph.shared_data.types.DataUpdates._
import org.elpaca_metagraph.shared_data.types.States._
import org.elpaca_metagraph.shared_data.types.Streak.StreakDataSourceAddress
import org.elpaca_metagraph.shared_data.validations.Validations.streakValidationsL0
import io.constellationnetwork.schema.balance.Amount
import io.constellationnetwork.schema.epoch.EpochProgress
import io.constellationnetwork.security.signature.Signed
import org.typelevel.log4cats.Logger

object StreakCombiner {
  private val level1Streak = NonNegLong(1L)
  private val level2Streak = NonNegLong(2L)
  private val level3Streak = NonNegLong(3L)

  private val randomStringLength: Int = 20

  private def getCurrentStreakDataSource(
    currentCalculatedState: Map[DataSourceType, DataSource]
  ): StreakDataSource = {
    currentCalculatedState
      .get(DataSourceType.Streak) match {
      case Some(streakDataSource: StreakDataSource) => streakDataSource
      case _ => StreakDataSource(Map.empty)
    }
  }

  def updateStateStreak[F[_] : Async : Logger](
    currentCalculatedState: Map[DataSourceType, DataSource],
    currentEpochProgress  : EpochProgress,
    signedStreakUpdate    : Signed[StreakUpdate],
    applicationConfig     : ApplicationConfig
  ): F[StreakDataSource] = {
    val streakDataSource = getCurrentStreakDataSource(currentCalculatedState)
    streakValidationsL0(signedStreakUpdate, streakDataSource, applicationConfig, currentEpochProgress) match {
      case Validated.Invalid(errors) =>
        Logger[F].warn(s"Could not update streak, reasons: $errors").as(streakDataSource)
      case Validated.Valid(_) =>
        case class StreakInfo(streakDays: NonNegLong, rewardAmount: Amount)

        val streakUpdate = signedStreakUpdate.value
        val streakDataSourceAddress =
          streakDataSource.existingWallets.get(streakUpdate.address) match {
            case Some(streakDataSourceAddress) => streakDataSourceAddress
            case None => StreakDataSourceAddress.empty
          }

        if (!isNewDay(streakDataSourceAddress.epochProgressToReward, currentEpochProgress)) {
          Logger[F].warn(s"This address already claimed reward of the day, ignoring").as(
            streakDataSource
          )
        } else {
          def shouldResetStreak: Boolean =
            currentEpochProgress.value.value - streakDataSourceAddress.epochProgressToReward.value.value > (epochProgressOneDay * 2)

          def streakBetween(min: Long, max: Long, updatedStreakDays: NonNegLong): Boolean =
            updatedStreakDays.value >= min && updatedStreakDays.value <= max

          def calculateRewardAmount(streakDays: NonNegLong): Amount = {
            if (streakBetween(1L, 4L, streakDays)) Amount(level1Streak)
            else if (streakBetween(5L, 10L, streakDays)) Amount(level2Streak)
            else Amount(level3Streak)
          }

          def getUpdateStreakInfo(streakDays: NonNegLong, ignoreReset: Boolean): StreakInfo = {
            if (shouldResetStreak && !ignoreReset) {
              StreakInfo(level1Streak, toTokenAmountFormat(Amount(level1Streak)))
            } else {
              val updatedStreakDays = NonNegLong.unsafeFrom(streakDays.value + 1L)
              val rewardAmount = toTokenAmountFormat(calculateRewardAmount(updatedStreakDays))
              StreakInfo(updatedStreakDays, rewardAmount)
            }
          }

          def updateStreakDataSource(streakInfo: StreakInfo, nextStreakInfo: StreakInfo): F[StreakDataSourceAddress] = {
            val totalEarned = Amount(
              NonNegLong.unsafeFrom(streakDataSourceAddress.totalEarned.value.value + streakInfo.rewardAmount.value.value)
            )

            randomString(
              s"${streakUpdate.address.value.value}-${currentEpochProgress.value.value.toString}",
              randomStringLength
            ).map { token =>
              streakDataSourceAddress
                .focus(_.dailyEpochProgress)
                .replace(currentEpochProgress)
                .focus(_.epochProgressToReward)
                .replace(currentEpochProgress)
                .focus(_.amountToReward)
                .replace(streakInfo.rewardAmount)
                .focus(_.totalEarned)
                .replace(totalEarned)
                .focus(_.nextClaimReward)
                .replace(nextStreakInfo.rewardAmount)
                .focus(_.streakDays)
                .replace(streakInfo.streakDays)
                .focus(_.nextToken)
                .replace(token.some)
            }
          }

          val currentStreakInfo = getUpdateStreakInfo(streakDataSourceAddress.streakDays, ignoreReset = false)
          val nextStreakInfo = getUpdateStreakInfo(currentStreakInfo.streakDays, ignoreReset = true)
          updateStreakDataSource(currentStreakInfo, nextStreakInfo).flatMap { updatedDataSourceAddress =>
            Logger[F].info(s"Claiming reward of the address ${streakUpdate.address}. Streak: ${currentStreakInfo.streakDays}").as(
              streakDataSource
                .focus(_.existingWallets)
                .modify(_.updated(streakUpdate.address, updatedDataSourceAddress)))
          }
        }
    }
  }
}
