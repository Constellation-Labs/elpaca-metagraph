package org.proof_of_attendance_metagraph.data_l1

import cats.data.NonEmptyList
import cats.effect.Async
import cats.syntax.functor._
import cats.syntax.applicative.catsSyntaxApplicativeId
import cats.syntax.validated._
import io.circe.{Decoder, Encoder}
import org.http4s.circe.CirceEntityCodec.circeEntityDecoder
import org.http4s.{EntityDecoder, HttpRoutes}
import org.proof_of_attendance_metagraph.shared_data.calculated_state.CalculatedStateService
import org.proof_of_attendance_metagraph.shared_data.types.DataUpdates._
import org.proof_of_attendance_metagraph.shared_data.types.States._
import org.proof_of_attendance_metagraph.shared_data.types.codecs.DataUpdateCodec._
import org.tessellation.currency.dataApplication._
import org.tessellation.currency.dataApplication.dataApplication.{DataApplicationBlock, DataApplicationValidationErrorOr}
import org.tessellation.json.JsonSerializer
import org.tessellation.schema.SnapshotOrdinal
import org.tessellation.security.hash.Hash
import org.tessellation.security.signature.Signed

object DataL1Service {

  def make[F[+_] : Async : JsonSerializer](): F[BaseDataApplicationL1Service[F]] =
    for {
      calculatedStateService <- CalculatedStateService.make[F]
      dataApplicationL1Service = makeBaseDataApplicationL1Service(
        calculatedStateService
      )
    } yield dataApplicationL1Service

  private def makeBaseDataApplicationL1Service[F[+_] : Async : JsonSerializer](
    calculatedStateService: CalculatedStateService[F]
  ): BaseDataApplicationL1Service[F] = BaseDataApplicationL1Service(
    new DataApplicationL1Service[F, ProofOfAttendanceUpdate, ProofOfAttendanceOnChainState, ProofOfAttendanceCalculatedState] {
      override def validateData(
        state  : DataState[ProofOfAttendanceOnChainState, ProofOfAttendanceCalculatedState],
        updates: NonEmptyList[Signed[ProofOfAttendanceUpdate]]
      )(implicit context: L1NodeContext[F]): F[DataApplicationValidationErrorOr[Unit]] =
        ().validNec[DataApplicationValidationError].pure[F]

      override def validateUpdate(
        update: ProofOfAttendanceUpdate
      )(implicit context: L1NodeContext[F]): F[DataApplicationValidationErrorOr[Unit]] =
        ().validNec[DataApplicationValidationError].pure[F]

      override def combine(
        state  : DataState[ProofOfAttendanceOnChainState, ProofOfAttendanceCalculatedState],
        updates: List[Signed[ProofOfAttendanceUpdate]]
      )(implicit context: L1NodeContext[F]): F[DataState[ProofOfAttendanceOnChainState, ProofOfAttendanceCalculatedState]] =
        state.pure[F]

      override def routes(implicit context: L1NodeContext[F]): HttpRoutes[F] =
        HttpRoutes.empty

      override def dataEncoder: Encoder[ProofOfAttendanceUpdate] =
        implicitly[Encoder[ProofOfAttendanceUpdate]]

      override def dataDecoder: Decoder[ProofOfAttendanceUpdate] =
        implicitly[Decoder[ProofOfAttendanceUpdate]]

      override def calculatedStateEncoder: Encoder[ProofOfAttendanceCalculatedState] =
        implicitly[Encoder[ProofOfAttendanceCalculatedState]]

      override def calculatedStateDecoder: Decoder[ProofOfAttendanceCalculatedState] =
        implicitly[Decoder[ProofOfAttendanceCalculatedState]]

      override def signedDataEntityDecoder: EntityDecoder[F, Signed[ProofOfAttendanceUpdate]] =
        circeEntityDecoder

      override def serializeBlock(
        block: Signed[DataApplicationBlock]
      ): F[Array[Byte]] =
        JsonSerializer[F].serialize[Signed[DataApplicationBlock]](block)

      override def deserializeBlock(
        bytes: Array[Byte]
      ): F[Either[Throwable, Signed[DataApplicationBlock]]] =
        JsonSerializer[F].deserialize[Signed[DataApplicationBlock]](bytes)

      override def serializeState(
        state: ProofOfAttendanceOnChainState
      ): F[Array[Byte]] =
        JsonSerializer[F].serialize[ProofOfAttendanceOnChainState](state)

      override def deserializeState(
        bytes: Array[Byte]
      ): F[Either[Throwable, ProofOfAttendanceOnChainState]] =
        JsonSerializer[F].deserialize[ProofOfAttendanceOnChainState](bytes)

      override def serializeUpdate(
        update: ProofOfAttendanceUpdate
      ): F[Array[Byte]] =
        JsonSerializer[F].serialize[ProofOfAttendanceUpdate](update)

      override def deserializeUpdate(
        bytes: Array[Byte]
      ): F[Either[Throwable, ProofOfAttendanceUpdate]] =
        JsonSerializer[F].deserialize[ProofOfAttendanceUpdate](bytes)

      override def getCalculatedState(implicit context: L1NodeContext[F]): F[(SnapshotOrdinal, ProofOfAttendanceCalculatedState)] =
        calculatedStateService.get.map(calculatedState => (calculatedState.ordinal, calculatedState.state))

      override def setCalculatedState(
        ordinal: SnapshotOrdinal,
        state  : ProofOfAttendanceCalculatedState
      )(implicit context: L1NodeContext[F]): F[Boolean] =
        calculatedStateService.update(ordinal, state)

      override def hashCalculatedState(
        state: ProofOfAttendanceCalculatedState
      )(implicit context: L1NodeContext[F]): F[Hash] =
        calculatedStateService.hash(state)

      override def serializeCalculatedState(
        state: ProofOfAttendanceCalculatedState
      ): F[Array[Byte]] =
        JsonSerializer[F].serialize[ProofOfAttendanceCalculatedState](state)

      override def deserializeCalculatedState(
        bytes: Array[Byte]
      ): F[Either[Throwable, ProofOfAttendanceCalculatedState]] =
        JsonSerializer[F].deserialize[ProofOfAttendanceCalculatedState](bytes)
    }
  )
}
