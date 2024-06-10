package org.elpaca_metagraph.shared_data.types

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive

object ExistingWallets {
  @derive(encoder, decoder)
  case class ExistingWalletsDataSourceAddress(
    freshWalletRewarded: Boolean,
    holdingDAGRewarded : Boolean
  )

  object ExistingWalletsDataSourceAddress {
    def empty: ExistingWalletsDataSourceAddress = {
      ExistingWalletsDataSourceAddress(freshWalletRewarded = false, holdingDAGRewarded = false)
    }
  }
}
