package org.elpaca_metagraph.shared_data.types

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import org.tessellation.schema.address.Address
import org.tessellation.schema.balance.Amount
import org.tessellation.schema.epoch.EpochProgress

object OutflowTransactions {

  @derive(encoder, decoder)
  case class OutflowAddressToRewardInfo(
    txnHash              : String,
    addressToReward      : Address,
    epochProgressToReward: EpochProgress,
    amountToReward       : Amount,
  )

  @derive(encoder, decoder)
  case class OutflowTransactionsDataSourceAddress(
    addressesToReward       : List[OutflowAddressToRewardInfo],
    transactionsHashRewarded: List[String]
  )

  object OutflowTransactionsDataSourceAddress {
    def empty: OutflowTransactionsDataSourceAddress = OutflowTransactionsDataSourceAddress(List.empty, List.empty)
  }
}
