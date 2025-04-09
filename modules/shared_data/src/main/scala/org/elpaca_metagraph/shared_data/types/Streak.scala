package org.elpaca_metagraph.shared_data.types

import cats.syntax.all._
import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import eu.timepit.refined.types.numeric.NonNegLong
import org.elpaca_metagraph.shared_data.types.codecs.NonNegLongCodec._
import io.constellationnetwork.schema.balance.Amount
import io.constellationnetwork.schema.epoch.EpochProgress

object Streak {
  @derive(encoder, decoder)
  case class StreakDataSourceAddress(
    dailyEpochProgress   : EpochProgress,
    epochProgressToReward: EpochProgress,
    amountToReward       : Amount,
    totalEarned          : Amount,
    nextClaimReward      : Amount,
    streakDays           : NonNegLong,
    nextToken            : Option[String]
  )

  object StreakDataSourceAddress {
    def empty: StreakDataSourceAddress = StreakDataSourceAddress(
      EpochProgress.MinValue,
      EpochProgress.MinValue,
      Amount.empty,
      Amount.empty,
      Amount.empty,
      NonNegLong.MinValue,
      none
    )
  }
}
