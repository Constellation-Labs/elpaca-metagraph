package org.proof_of_attendance_metagraph.shared_data.types

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import io.circe.generic.auto._
import io.circe.generic.extras.Configuration
import io.circe.{KeyDecoder, KeyEncoder}
import org.proof_of_attendance_metagraph.shared_data.types.DataUpdates.ProofOfAttendanceUpdate
import org.proof_of_attendance_metagraph.shared_data.types.States.DataSourceType.DataSourceType
import org.tessellation.currency.dataApplication.{DataCalculatedState, DataOnChainState}
import org.tessellation.schema.address.Address
import org.tessellation.schema.epoch.EpochProgress

object States {
  implicit val config: Configuration = Configuration.default.withDefaults

  @derive(encoder, decoder)
  object DataSourceType extends Enumeration {
    type DataSourceType = Value
    val Exolix, Simplex, IntegrationnetNodeOperator, WalletCreation = Value
    implicit val dataSourceTypeKeyEncoder: KeyEncoder[DataSourceType] = KeyEncoder.encodeKeyString.contramap(_.toString)
    implicit val dataSourceTypeKeyDecoder: KeyDecoder[DataSourceType] = KeyDecoder.decodeKeyString.map {
      case "Exolix" => Exolix
      case "Simplex" => Simplex
      case "IntegrationnetNodeOperator" => IntegrationnetNodeOperator
      case "WalletCreation" => WalletCreation
    }
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
  case class ProofOfAttendanceOnChainState(
    updates: List[ProofOfAttendanceUpdate]
  ) extends DataOnChainState

  @derive(encoder, decoder)
  case class ProofOfAttendanceCalculatedState(
    dataSources: Map[DataSourceType, DataSource]
  ) extends DataCalculatedState
}
