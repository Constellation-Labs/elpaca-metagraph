package org.proof_of_attendance_metagraph.shared_data.types

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import io.circe.generic.auto._
import org.proof_of_attendance_metagraph.shared_data.types.DataUpdates.ProofOfAttendanceUpdate
import org.tessellation.currency.dataApplication.{DataCalculatedState, DataOnChainState}
import org.tessellation.schema.address.Address
import org.tessellation.schema.epoch.EpochProgress

object States {
  @derive(encoder, decoder)
  sealed trait DataSources {
    val name: String
    val epochProgressToReward: EpochProgress
    val amountToReward: Long
  }

  @derive(encoder, decoder)
  case class ExolixDataSource(
    epochProgressToReward: EpochProgress,
    amountToReward       : Long,
    latestTransactionsIds: Set[String],
    olderTransactionsIds : Set[String]
  ) extends DataSources {
    override val name: String = "ExolixDataSource"

    override def equals(obj: Any): Boolean = obj match {
      case that: ExolixDataSource => this.name == that.name
      case _ => false
    }

    override def hashCode(): Int = name.hashCode()
  }

  @derive(encoder, decoder)
  case class SimplexDataSource(
    epochProgressToReward: EpochProgress,
    amountToReward       : Long,
    latestEventsIds      : Set[String],
    olderEventsIds       : Set[String]
  ) extends DataSources {
    override val name: String = "SimplexDataSource"

    override def equals(obj: Any): Boolean = obj match {
      case that: SimplexDataSource => this.name == that.name
      case _ => false
    }

    override def hashCode(): Int = name.hashCode()
  }

  @derive(encoder, decoder)
  case class TwitterDataSource(
    epochProgressToReward     : EpochProgress,
    amountToReward            : Long,
    twitterApiResponseAsString: String
  ) extends DataSources {
    override val name: String = "TwitterDataSource"

    override def equals(obj: Any): Boolean = obj match {
      case that: TwitterDataSource => this.name == that.name
      case _ => false
    }

    override def hashCode(): Int = name.hashCode()
  }

  @derive(encoder, decoder)
  case class IntegrationnetNodeOperatorDataSource(
    epochProgressToReward: EpochProgress,
    amountToReward       : Long,
    daysInQueue          : Long,
  ) extends DataSources {
    override val name: String = "IntegrationnetNodeOperatorDataSource"

    override def equals(obj: Any): Boolean = obj match {
      case that: IntegrationnetNodeOperatorDataSource => this.name == that.name
      case _ => false
    }

    override def hashCode(): Int = name.hashCode()
  }

  @derive(encoder, decoder)
  case class NewWalletCreationDataSource(
    epochProgressToReward            : EpochProgress,
    amountToReward                   : Long,
    newWalletCreationResponseAsString: String
  ) extends DataSources {
    override val name: String = "NewWalletCreationDataSource"

    override def equals(obj: Any): Boolean = obj match {
      case that: NewWalletCreationDataSource => this.name == that.name
      case _ => false
    }

    override def hashCode(): Int = name.hashCode()
  }

  @derive(encoder, decoder)
  case class ProofOfAttendanceOnChainState(
    updates: List[ProofOfAttendanceUpdate]
  ) extends DataOnChainState


  @derive(encoder, decoder)
  case class ProofOfAttendanceCalculatedState(
    addresses: Map[Address, Set[DataSources]]
  ) extends DataCalculatedState

}
