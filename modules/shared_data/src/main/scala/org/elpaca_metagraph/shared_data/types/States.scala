package org.elpaca_metagraph.shared_data.types

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import enumeratum.values.{StringCirceEnum, StringEnum, StringEnumEntry}
import io.circe.generic.auto._
import io.circe.generic.extras.Configuration
import org.elpaca_metagraph.shared_data.types.DataUpdates.ElpacaUpdate
import org.tessellation.currency.dataApplication.{DataCalculatedState, DataOnChainState}
import org.tessellation.schema.address.Address
import org.tessellation.schema.epoch.EpochProgress

object States {
  implicit val config: Configuration = Configuration.default.withDefaults

  @derive(encoder, decoder)
  sealed abstract class DataSourceType(val value: String) extends StringEnumEntry

  object DataSourceType extends StringEnum[DataSourceType] with StringCirceEnum[DataSourceType] {
    val values = findValues

    case object Exolix extends DataSourceType("Exolix")

    case object Simplex extends DataSourceType("Simplex")

    case object IntegrationnetNodeOperator extends DataSourceType("IntegrationnetNodeOperator")

    case object WalletCreation extends DataSourceType("WalletCreation")
  }


  @derive(encoder, decoder)
  sealed trait DataSource

  @derive(encoder, decoder)
  case class ExolixDataSourceAddress(
    epochProgressToReward: EpochProgress,
    amountToReward       : Long,
    latestTransactionsIds: Set[String],
    olderTransactionsIds : Set[String]
  )

  @derive(encoder, decoder)
  case class ExolixDataSource(
    addresses: Map[Address, ExolixDataSourceAddress]
  ) extends DataSource

  @derive(encoder, decoder)
  case class SimplexDataSourceAddress(
    epochProgressToReward: EpochProgress,
    amountToReward       : Long,
    latestTransactionsIds: Set[String],
    olderTransactionsIds : Set[String]
  )

  @derive(encoder, decoder)
  case class SimplexDataSource(
    addresses: Map[Address, SimplexDataSourceAddress]
  ) extends DataSource

  @derive(encoder, decoder)
  case class IntegrationnetNodeOperatorDataSourceAddress(
    epochProgressToReward: EpochProgress,
    amountToReward       : Long,
    daysInQueue          : Long
  )

  @derive(encoder, decoder)
  case class IntegrationnetNodeOperatorDataSource(
    addresses: Map[Address, IntegrationnetNodeOperatorDataSourceAddress]
  ) extends DataSource

  @derive(encoder, decoder)
  case class WalletCreationDataSourceAddress(
    epochProgressToReward  : Option[EpochProgress],
    amountToReward         : Long,
    registeredEpochProgress: EpochProgress,
    balance                : Long
  )

  @derive(encoder, decoder)
  case class WalletCreationDataSource(
    addressesToReward: Map[Address, WalletCreationDataSourceAddress],
    addressesRewarded: Set[Address]
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
