package org.proof_of_attendance_metagraph.l0

import cats.data.NonEmptyList
import cats.effect.Async
import cats.syntax.applicative.catsSyntaxApplicativeId
import cats.syntax.functor._
import io.circe.{Decoder, Encoder}
import org.http4s.circe.CirceEntityCodec.circeEntityDecoder
import org.http4s.{EntityDecoder, HttpRoutes}
import org.proof_of_attendance_metagraph.l0.custom_routes.CustomRoutes
import org.proof_of_attendance_metagraph.shared_data.LifecycleSharedFunctions
import org.proof_of_attendance_metagraph.shared_data.calculated_state.CalculatedStateService
import org.proof_of_attendance_metagraph.shared_data.types.DataUpdates._
import org.proof_of_attendance_metagraph.shared_data.types.States._
import org.proof_of_attendance_metagraph.shared_data.types.codecs.DataUpdateCodec._
import org.proof_of_attendance_metagraph.shared_data.validations.Errors.valid
import org.tessellation.currency.dataApplication._
import org.tessellation.currency.dataApplication.dataApplication.{DataApplicationBlock, DataApplicationValidationErrorOr}
import org.tessellation.json.JsonSerializer
import org.tessellation.schema.SnapshotOrdinal
import org.tessellation.security.hash.Hash
import org.tessellation.security.signature.Signed

object MetagraphL0Service {

  def make[F[+_] : Async : JsonSerializer](): F[BaseDataApplicationL0Service[F]] =
    for {
      calculatedStateService <- CalculatedStateService.make[F]
      dataApplicationL1Service = makeBaseDataApplicationL0Service(
        calculatedStateService
      )
    } yield dataApplicationL1Service

  private def makeBaseDataApplicationL0Service[F[+_] : Async : JsonSerializer](
    calculatedStateService: CalculatedStateService[F]
  ): BaseDataApplicationL0Service[F] =
    BaseDataApplicationL0Service(
      new DataApplicationL0Service[F, ProofOfAttendanceUpdate, ProofOfAttendanceOnChainState, ProofOfAttendanceCalculatedState] {
        override def genesis: DataState[ProofOfAttendanceOnChainState, ProofOfAttendanceCalculatedState] =
          DataState(ProofOfAttendanceOnChainState(List.empty), ProofOfAttendanceCalculatedState(Map.empty))

        override def validateUpdate(
          update: ProofOfAttendanceUpdate
        )(implicit context: L0NodeContext[F]): F[DataApplicationValidationErrorOr[Unit]] =
          valid.pure

        override def validateData(
          state  : DataState[ProofOfAttendanceOnChainState, ProofOfAttendanceCalculatedState],
          updates: NonEmptyList[Signed[ProofOfAttendanceUpdate]]
        )(implicit context: L0NodeContext[F]): F[DataApplicationValidationErrorOr[Unit]] =
          valid.pure

        override def combine(
          state  : DataState[ProofOfAttendanceOnChainState, ProofOfAttendanceCalculatedState],
          updates: List[Signed[ProofOfAttendanceUpdate]]
        )(implicit context: L0NodeContext[F]): F[DataState[ProofOfAttendanceOnChainState, ProofOfAttendanceCalculatedState]] =
          LifecycleSharedFunctions.combine[F](
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

        override def getCalculatedState(implicit context: L0NodeContext[F]): F[(SnapshotOrdinal, ProofOfAttendanceCalculatedState)] =
          calculatedStateService.get.map(calculatedState => (calculatedState.ordinal, calculatedState.state))

        override def setCalculatedState(
          ordinal: SnapshotOrdinal,
          state  : ProofOfAttendanceCalculatedState
        )(implicit context: L0NodeContext[F]): F[Boolean] =
          calculatedStateService.update(ordinal, state)

        override def hashCalculatedState(
          state: ProofOfAttendanceCalculatedState
        )(implicit context: L0NodeContext[F]): F[Hash] =
          calculatedStateService.hash(state)

        override def routes(implicit context: L0NodeContext[F]): HttpRoutes[F] =
          CustomRoutes[F](calculatedStateService).public

        override def serializeCalculatedState(
          state: ProofOfAttendanceCalculatedState
        ): F[Array[Byte]] =
          JsonSerializer[F].serialize[ProofOfAttendanceCalculatedState](state)

        override def deserializeCalculatedState(
          bytes: Array[Byte]
        ): F[Either[Throwable, ProofOfAttendanceCalculatedState]] =
          JsonSerializer[F].deserialize[ProofOfAttendanceCalculatedState](bytes)
      })
}
