package org.elpaca_metagraph.shared_data.types

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import enumeratum.values.{StringCirceEnum, StringEnum, StringEnumEntry}
import io.circe.generic.auto._
import io.circe.generic.extras.Configuration
import org.elpaca_metagraph.shared_data.types.DataUpdates.ElpacaUpdate
import org.elpaca_metagraph.shared_data.types.ExistingWallets.ExistingWalletsDataSourceAddress
import org.elpaca_metagraph.shared_data.types.Exolix.ExolixDataSourceAddress
import org.elpaca_metagraph.shared_data.types.FreshWallet.FreshWalletDataSourceAddress
import org.elpaca_metagraph.shared_data.types.InflowTransactions.InflowTransactionsDataSourceAddress
import org.elpaca_metagraph.shared_data.types.IntegrationnetOperators.IntegrationnetNodeOperatorDataSourceAddress
import org.elpaca_metagraph.shared_data.types.OutflowTransactions.OutflowTransactionsDataSourceAddress
import org.elpaca_metagraph.shared_data.types.Simplex.SimplexDataSourceAddress
import org.elpaca_metagraph.shared_data.types.WalletCreationHoldingDAG.WalletCreationHoldingDAGDataSourceAddress
import org.elpaca_metagraph.shared_data.types.X.XDataSourceAddress
import org.tessellation.currency.dataApplication.{DataCalculatedState, DataOnChainState}
import org.tessellation.schema.address.Address

object States {
  implicit val config: Configuration = Configuration.default.withDefaults

  @derive(encoder, decoder)
  sealed abstract class DataSourceType(val value: String) extends StringEnumEntry

  object DataSourceType extends StringEnum[DataSourceType] with StringCirceEnum[DataSourceType] {
    val values = findValues

    case object Exolix extends DataSourceType("Exolix")

    case object Simplex extends DataSourceType("Simplex")

    case object IntegrationnetNodeOperator extends DataSourceType("IntegrationnetNodeOperator")

    case object ExistingWallets extends DataSourceType("ExistingWallets")

    case object WalletCreationHoldingDAG extends DataSourceType("WalletCreationHoldingDAG")

    case object FreshWallet extends DataSourceType("FreshWallet")

    case object InflowTransactions extends DataSourceType("InflowTransactions")

    case object OutflowTransactions extends DataSourceType("OutflowTransactions")

    case object X extends DataSourceType("X")
  }

  @derive(encoder, decoder)
  sealed trait DataSource

  @derive(encoder, decoder)
  case class ExolixDataSource(
    addresses: Map[Address, ExolixDataSourceAddress]
  ) extends DataSource

  @derive(encoder, decoder)
  case class SimplexDataSource(
    addresses: Map[Address, SimplexDataSourceAddress]
  ) extends DataSource

  @derive(encoder, decoder)
  case class IntegrationnetNodeOperatorDataSource(
    addresses: Map[Address, IntegrationnetNodeOperatorDataSourceAddress]
  ) extends DataSource

  @derive(encoder, decoder)
  case class WalletCreationHoldingDAGDataSource(
    addressesToReward: Map[Address, WalletCreationHoldingDAGDataSourceAddress]
  ) extends DataSource

  @derive(encoder, decoder)
  case class FreshWalletDataSource(
    addressesToReward: Map[Address, FreshWalletDataSourceAddress]
  ) extends DataSource

  @derive(encoder, decoder)
  case class ExistingWalletsDataSource(
    existingWallets: Map[Address, ExistingWalletsDataSourceAddress]
  ) extends DataSource

  @derive(encoder, decoder)
  case class InflowTransactionsDataSource(
    existingWallets: Map[Address, InflowTransactionsDataSourceAddress]
  ) extends DataSource

  @derive(encoder, decoder)
  case class OutflowTransactionsDataSource(
    existingWallets: Map[Address, OutflowTransactionsDataSourceAddress]
  ) extends DataSource

  @derive(encoder, decoder)
  case class XDataSource(
    existingWallets: Map[Address, XDataSourceAddress]
  ) extends DataSource

  @derive(encoder, decoder)
  case class ElpacaOnChainState(
    updates: List[ElpacaUpdate]
  ) extends DataOnChainState

  @derive(encoder, decoder)
  case class ElpacaCalculatedState(
    dataSources: Map[DataSourceType, DataSource]
  ) extends DataCalculatedState
}
