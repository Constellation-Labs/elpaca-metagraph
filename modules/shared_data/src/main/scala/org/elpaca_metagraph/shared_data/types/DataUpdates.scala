package org.elpaca_metagraph.shared_data.types

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import io.circe.generic.auto._
import org.elpaca_metagraph.shared_data.types.Exolix.ExolixTransaction
import org.elpaca_metagraph.shared_data.types.IntegrationnetOperators.OperatorInQueue
import org.elpaca_metagraph.shared_data.types.Simplex.SimplexEvent
import org.tessellation.currency.dataApplication.DataUpdate
import org.tessellation.schema.address.Address
import org.tessellation.schema.balance.Amount

object DataUpdates {
  @derive(encoder, decoder)
  sealed trait ElpacaUpdate extends DataUpdate {
    val address: Address
  }

  @derive(encoder, decoder)
  case class ExolixUpdate(
    address           : Address,
    exolixTransactions: Set[ExolixTransaction]
  ) extends ElpacaUpdate

  @derive(encoder, decoder)
  case class SimplexUpdate(
    address      : Address,
    simplexEvents: Set[SimplexEvent]
  ) extends ElpacaUpdate

  @derive(encoder, decoder)
  case class IntegrationnetNodeOperatorUpdate(
    address        : Address,
    operatorInQueue: OperatorInQueue
  ) extends ElpacaUpdate

  @derive(encoder, decoder)
  case class WalletCreationHoldingDAGUpdate(
    address: Address,
    balance: Amount
  ) extends ElpacaUpdate

  @derive(encoder, decoder)
  case class FreshWalletUpdate(
    address: Address
  ) extends ElpacaUpdate

  @derive(encoder, decoder)
  case class InflowTransactionsUpdate(
    address      : Address,
    txnHash      : String,
    rewardAddress: Address,
    rewardAmount : Amount
  ) extends ElpacaUpdate

  @derive(encoder, decoder)
  case class OutflowTransactionsUpdate(
    address      : Address,
    txnHash      : String,
    rewardAddress: Address,
    rewardAmount : Amount
  ) extends ElpacaUpdate

  @derive(encoder, decoder)
  case class XUpdate(
    address   : Address,
    searchText: String,
    postId    : String
  ) extends ElpacaUpdate
}
