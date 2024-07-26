package org.elpaca_metagraph.shared_data.types

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import org.tessellation.schema.address.Address
import org.tessellation.schema.balance.Amount
import org.tessellation.schema.epoch.EpochProgress

object InflowTransactions {
  @derive(encoder, decoder)
  case class InflowAddressToRewardInfo(
    txnHash              : String,
    addressToReward      : Address,
    epochProgressToReward: EpochProgress,
    amountToReward       : Amount,
  )

  @derive(encoder, decoder)
  case class InflowTransactionsDataSourceAddress(
    addressesToReward       : List[InflowAddressToRewardInfo],
    transactionsHashRewarded: List[String]
  )

  object InflowTransactionsDataSourceAddress {
    def empty: InflowTransactionsDataSourceAddress = InflowTransactionsDataSourceAddress(List.empty, List.empty)
  }
}
