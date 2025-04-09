package org.elpaca_metagraph.shared_data.types

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import io.constellationnetwork.schema.balance.Amount
import io.constellationnetwork.schema.epoch.EpochProgress

object FreshWallet {
  @derive(encoder, decoder)
  case class FreshWalletDataSourceAddress(
    epochProgressToReward: EpochProgress,
    amountToReward       : Amount,
  )
}
