package org.proof_of_attendance_metagraph.l0

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import cats.syntax.applicative._
import cats.syntax.option._
import cats.syntax.validated._
import io.circe.{Decoder, Encoder}
import org.http4s.circe.CirceEntityCodec.circeEntityDecoder
import org.http4s.{EntityDecoder, HttpRoutes}
import org.proof_of_attendance_metagraph.l0.custom_routes.CustomRoutes
import org.proof_of_attendance_metagraph.l0.rewards.ProofOfAttendanceRewards
import org.proof_of_attendance_metagraph.shared_data.LifecycleSharedFunctions
import org.proof_of_attendance_metagraph.shared_data.calculated_state.CalculatedStateService
import org.proof_of_attendance_metagraph.shared_data.deserializers.Deserializers
import org.proof_of_attendance_metagraph.shared_data.serializers.Serializers
import org.proof_of_attendance_metagraph.shared_data.types.DataUpdates._
import org.proof_of_attendance_metagraph.shared_data.types.States._
import org.tessellation.currency.dataApplication._
import org.tessellation.currency.dataApplication.dataApplication.{DataApplicationBlock, DataApplicationValidationErrorOr}
import org.tessellation.currency.l0.CurrencyL0App
import org.tessellation.currency.schema.currency.{CurrencyIncrementalSnapshot, CurrencySnapshotStateProof}
import org.tessellation.ext.cats.effect.ResourceIO
import org.tessellation.node.shared.domain.rewards.Rewards
import org.tessellation.node.shared.snapshot.currency.CurrencySnapshotEvent
import org.tessellation.schema.SnapshotOrdinal
import org.tessellation.schema.cluster.ClusterId
import org.tessellation.schema.semver.{MetagraphVersion, TessellationVersion}
import org.tessellation.security.SecurityProvider
import org.tessellation.security.hash.Hash
import org.tessellation.security.signature.Signed

import java.util.UUID

object Main extends CurrencyL0App(
  "currency-l0",
  "currency L0 node",
  ClusterId(UUID.fromString("517c3a05-9219-471b-a54c-21b7d72f4ae5")),
  tessellationVersion = TessellationVersion.unsafeFrom(org.tessellation.BuildInfo.version),
  metagraphVersion = MetagraphVersion.unsafeFrom(org.proof_of_attendance_metagraph.l0.BuildInfo.version)
) {
  private def makeBaseDataApplicationL0Service(
    calculatedStateService: CalculatedStateService[IO]
  ): BaseDataApplicationL0Service[IO] =
    BaseDataApplicationL0Service(
      new DataApplicationL0Service[IO, ProofOfAttendanceUpdate, ProofOfAttendanceOnChainState, ProofOfAttendanceCalculatedState] {
        override def genesis: DataState[ProofOfAttendanceOnChainState, ProofOfAttendanceCalculatedState] =
          DataState(ProofOfAttendanceOnChainState(List.empty), ProofOfAttendanceCalculatedState(Map.empty))

        override def validateUpdate(
          update: ProofOfAttendanceUpdate
        )(implicit context: L0NodeContext[IO]): IO[DataApplicationValidationErrorOr[Unit]] =
          ().validNec.pure[IO]

        override def validateData(
          state  : DataState[ProofOfAttendanceOnChainState, ProofOfAttendanceCalculatedState],
          updates: NonEmptyList[Signed[ProofOfAttendanceUpdate]]
        )(implicit context: L0NodeContext[IO]): IO[DataApplicationValidationErrorOr[Unit]] =
          ().validNec.pure[IO]

        override def combine(
          state  : DataState[ProofOfAttendanceOnChainState, ProofOfAttendanceCalculatedState],
          updates: List[Signed[ProofOfAttendanceUpdate]]
        )(implicit context: L0NodeContext[IO]): IO[DataState[ProofOfAttendanceOnChainState, ProofOfAttendanceCalculatedState]] =
          LifecycleSharedFunctions.combine[IO](
            state,
            updates
          )

        override def dataEncoder: Encoder[ProofOfAttendanceUpdate] =
          implicitly[Encoder[ProofOfAttendanceUpdate]]

        override def calculatedStateEncoder: Encoder[ProofOfAttendanceCalculatedState] =
          implicitly[Encoder[ProofOfAttendanceCalculatedState]]

        override def dataDecoder: Decoder[ProofOfAttendanceUpdate] =
          implicitly[Decoder[ProofOfAttendanceUpdate]]

        override def calculatedStateDecoder: Decoder[ProofOfAttendanceCalculatedState] =
          implicitly[Decoder[ProofOfAttendanceCalculatedState]]

        override def signedDataEntityDecoder: EntityDecoder[IO, Signed[ProofOfAttendanceUpdate]] =
          circeEntityDecoder

        override def serializeBlock(
          block: Signed[DataApplicationBlock]
        ): IO[Array[Byte]] =
          IO(Serializers.serializeBlock(block)(dataEncoder.asInstanceOf[Encoder[DataUpdate]]))

        override def deserializeBlock(
          bytes: Array[Byte]
        ): IO[Either[Throwable, Signed[DataApplicationBlock]]] =
          IO(Deserializers.deserializeBlock(bytes)(dataDecoder.asInstanceOf[Decoder[DataUpdate]]))

        override def serializeState(
          state: ProofOfAttendanceOnChainState
        ): IO[Array[Byte]] =
          IO(Serializers.serializeState(state))

        override def deserializeState(
          bytes: Array[Byte]
        ): IO[Either[Throwable, ProofOfAttendanceOnChainState]] =
          IO(Deserializers.deserializeState(bytes))

        override def serializeUpdate(
          update: ProofOfAttendanceUpdate
        ): IO[Array[Byte]] =
          IO(Serializers.serializeUpdate(update))

        override def deserializeUpdate(
          bytes: Array[Byte]
        ): IO[Either[Throwable, ProofOfAttendanceUpdate]] =
          IO(Deserializers.deserializeUpdate(bytes))

        override def getCalculatedState(implicit context: L0NodeContext[IO]): IO[(SnapshotOrdinal, ProofOfAttendanceCalculatedState)] =
          calculatedStateService.get.map(calculatedState => (calculatedState.ordinal, calculatedState.state))

        override def setCalculatedState(
          ordinal: SnapshotOrdinal,
          state  : ProofOfAttendanceCalculatedState
        )(implicit context: L0NodeContext[IO]): IO[Boolean] =
          calculatedStateService.update(ordinal, state)

        override def hashCalculatedState(
          state: ProofOfAttendanceCalculatedState
        )(implicit context: L0NodeContext[IO]): IO[Hash] =
          calculatedStateService.hash(state)

        override def routes(implicit context: L0NodeContext[IO]): HttpRoutes[IO] =
          CustomRoutes[IO](calculatedStateService).public

        override def serializeCalculatedState(
          state: ProofOfAttendanceCalculatedState
        ): IO[Array[Byte]] =
          IO(Serializers.serializeCalculatedState(state))

        override def deserializeCalculatedState(
          bytes: Array[Byte]
        ): IO[Either[Throwable, ProofOfAttendanceCalculatedState]] =
          IO(Deserializers.deserializeCalculatedState(bytes))
      })

  override def dataApplication: Option[Resource[IO, BaseDataApplicationL0Service[IO]]] =
    CalculatedStateService.make[IO].map(makeBaseDataApplicationL0Service).asResource.some

  override def rewards(implicit sp: SecurityProvider[IO]): Option[Rewards[IO, CurrencySnapshotStateProof, CurrencyIncrementalSnapshot, CurrencySnapshotEvent]] =
    ProofOfAttendanceRewards.make[IO]().some
}

