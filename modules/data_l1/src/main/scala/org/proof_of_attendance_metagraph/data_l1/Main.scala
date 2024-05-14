package org.proof_of_attendance_metagraph.data_l1

import cats.data.NonEmptyList
import cats.effect.std.Supervisor
import cats.effect.{IO, Resource}
import cats.implicits.catsSyntaxOptionId
import cats.syntax.applicative.catsSyntaxApplicativeId
import cats.syntax.validated._
import io.circe.{Decoder, Encoder}
import org.http4s.circe.CirceEntityCodec.circeEntityDecoder
import org.http4s.{EntityDecoder, HttpRoutes}
import org.proof_of_attendance_metagraph.data_l1.daemons.DaemonApis
import org.proof_of_attendance_metagraph.shared_data.Utils.loadKeyPair
import org.proof_of_attendance_metagraph.shared_data.app.ApplicationConfigOps
import org.proof_of_attendance_metagraph.shared_data.calculated_state.CalculatedStateService
import org.proof_of_attendance_metagraph.shared_data.deserializers.Deserializers
import org.proof_of_attendance_metagraph.shared_data.serializers.Serializers
import org.proof_of_attendance_metagraph.shared_data.types.DataUpdates._
import org.proof_of_attendance_metagraph.shared_data.types.States._
import org.proof_of_attendance_metagraph.shared_data.types.codecs.JsonBinaryCodec
import org.tessellation.currency.dataApplication._
import org.tessellation.currency.dataApplication.dataApplication.{DataApplicationBlock, DataApplicationValidationErrorOr}
import org.tessellation.currency.l1.CurrencyL1App
import org.tessellation.ext.cats.effect.ResourceIO
import org.tessellation.json.JsonSerializer
import org.tessellation.schema.SnapshotOrdinal
import org.tessellation.schema.cluster.ClusterId
import org.tessellation.schema.semver.{MetagraphVersion, TessellationVersion}
import org.tessellation.security.SecurityProvider
import org.tessellation.security.hash.Hash
import org.tessellation.security.signature.Signed

import java.util.UUID

object Main extends CurrencyL1App(
  "currency-data_l1",
  "currency data L1 node",
  ClusterId(UUID.fromString("517c3a05-9219-471b-a54c-21b7d72f4ae5")),
  tessellationVersion = TessellationVersion.unsafeFrom(org.tessellation.BuildInfo.version),
  metagraphVersion = MetagraphVersion.unsafeFrom(org.proof_of_attendance_metagraph.data_l1.BuildInfo.version)
) {

  private def makeBaseDataApplicationL1Service(
    calculatedStateService: CalculatedStateService[IO]
  ): BaseDataApplicationL1Service[IO] = BaseDataApplicationL1Service(
    new DataApplicationL1Service[IO, ProofOfAttendanceUpdate, ProofOfAttendanceOnChainState, ProofOfAttendanceCalculatedState] {
      override def validateData(
        state  : DataState[ProofOfAttendanceOnChainState, ProofOfAttendanceCalculatedState],
        updates: NonEmptyList[Signed[ProofOfAttendanceUpdate]]
      )(implicit context: L1NodeContext[IO]): IO[DataApplicationValidationErrorOr[Unit]] =
        ().validNec.pure[IO]

      override def validateUpdate(
        update: ProofOfAttendanceUpdate
      )(implicit context: L1NodeContext[IO]): IO[DataApplicationValidationErrorOr[Unit]] =
        ().validNec.pure[IO]

      override def combine(
        state  : DataState[ProofOfAttendanceOnChainState, ProofOfAttendanceCalculatedState],
        updates: List[Signed[ProofOfAttendanceUpdate]]
      )(implicit context: L1NodeContext[IO]): IO[DataState[ProofOfAttendanceOnChainState, ProofOfAttendanceCalculatedState]] =
        state.pure[IO]

      override def routes(implicit context: L1NodeContext[IO]): HttpRoutes[IO] =
        HttpRoutes.empty

      override def dataEncoder: Encoder[ProofOfAttendanceUpdate] =
        implicitly[Encoder[ProofOfAttendanceUpdate]]

      override def dataDecoder: Decoder[ProofOfAttendanceUpdate] =
        implicitly[Decoder[ProofOfAttendanceUpdate]]

      override def calculatedStateEncoder: Encoder[ProofOfAttendanceCalculatedState] =
        implicitly[Encoder[ProofOfAttendanceCalculatedState]]

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

      override def getCalculatedState(implicit context: L1NodeContext[IO]): IO[(SnapshotOrdinal, ProofOfAttendanceCalculatedState)] =
        calculatedStateService.get.map(calculatedState => (calculatedState.ordinal, calculatedState.state))

      override def setCalculatedState(
        ordinal: SnapshotOrdinal,
        state  : ProofOfAttendanceCalculatedState
      )(implicit context: L1NodeContext[IO]): IO[Boolean] =
        calculatedStateService.update(ordinal, state)

      override def hashCalculatedState(
        state: ProofOfAttendanceCalculatedState
      )(implicit context: L1NodeContext[IO]): IO[Hash] =
        calculatedStateService.hash(state)

      override def serializeCalculatedState(
        state: ProofOfAttendanceCalculatedState
      ): IO[Array[Byte]] =
        IO(Serializers.serializeCalculatedState(state))

      override def deserializeCalculatedState(
        bytes: Array[Byte]
      ): IO[Either[Throwable, ProofOfAttendanceCalculatedState]] =
        IO(Deserializers.deserializeCalculatedState(bytes))
    }
  )

  override def dataApplication: Option[Resource[IO, BaseDataApplicationL1Service[IO]]] = (for {
    implicit0(supervisor: Supervisor[IO]) <- Supervisor[IO]
    implicit0(sp: SecurityProvider[IO]) <- SecurityProvider.forAsync[IO]
    implicit0(json2bin: JsonSerializer[IO]) <- JsonBinaryCodec.forSync[IO].asResource

    config <- ApplicationConfigOps.readDefault[IO].asResource
    keyPair <- loadKeyPair[IO](config).asResource
    _ <- DaemonApis.make[IO](config, keyPair).spawnDaemons.asResource
    calculatedStateService <- CalculatedStateService.make[IO].asResource
  } yield makeBaseDataApplicationL1Service(calculatedStateService)).some
}
