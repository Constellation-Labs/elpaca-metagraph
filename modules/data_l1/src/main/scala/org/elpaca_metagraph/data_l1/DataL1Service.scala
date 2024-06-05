package org.elpaca_metagraph.data_l1

import cats.data.NonEmptyList
import cats.effect.Async
import cats.syntax.all._
import io.circe.{Decoder, Encoder}
import org.elpaca_metagraph.shared_data.LifecycleSharedFunctions
import org.elpaca_metagraph.shared_data.calculated_state.CalculatedStateService
import org.elpaca_metagraph.shared_data.types.DataUpdates._
import org.elpaca_metagraph.shared_data.types.States._
import org.elpaca_metagraph.shared_data.types.codecs.DataUpdateCodec._
import org.elpaca_metagraph.shared_data.validations.Errors.valid
import org.http4s.circe.CirceEntityCodec.circeEntityDecoder
import org.http4s.{EntityDecoder, HttpRoutes}
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
    new DataApplicationL1Service[F, ElpacaUpdate, ElpacaOnChainState, ElpacaCalculatedState] {
      override def validateData(
        state  : DataState[ElpacaOnChainState, ElpacaCalculatedState],
        updates: NonEmptyList[Signed[ElpacaUpdate]]
      )(implicit context: L1NodeContext[F]): F[DataApplicationValidationErrorOr[Unit]] =
        valid.pure

      override def validateUpdate(
        update: ElpacaUpdate
      )(implicit context: L1NodeContext[F]): F[DataApplicationValidationErrorOr[Unit]] =
        LifecycleSharedFunctions.validateUpdate(update)

      override def combine(
        state  : DataState[ElpacaOnChainState, ElpacaCalculatedState],
        updates: List[Signed[ElpacaUpdate]]
      )(implicit context: L1NodeContext[F]): F[DataState[ElpacaOnChainState, ElpacaCalculatedState]] =
        state.pure

      override def routes(implicit context: L1NodeContext[F]): HttpRoutes[F] =
        HttpRoutes.empty

      override def dataEncoder: Encoder[ElpacaUpdate] =
        implicitly[Encoder[ElpacaUpdate]]

      override def dataDecoder: Decoder[ElpacaUpdate] =
        implicitly[Decoder[ElpacaUpdate]]

      override def calculatedStateEncoder: Encoder[ElpacaCalculatedState] =
        implicitly[Encoder[ElpacaCalculatedState]]

      override def calculatedStateDecoder: Decoder[ElpacaCalculatedState] =
        implicitly[Decoder[ElpacaCalculatedState]]

      override def signedDataEntityDecoder: EntityDecoder[F, Signed[ElpacaUpdate]] =
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
        state: ElpacaOnChainState
      ): F[Array[Byte]] =
        JsonSerializer[F].serialize[ElpacaOnChainState](state)

      override def deserializeState(
        bytes: Array[Byte]
      ): F[Either[Throwable, ElpacaOnChainState]] =
        JsonSerializer[F].deserialize[ElpacaOnChainState](bytes)

      override def serializeUpdate(
        update: ElpacaUpdate
      ): F[Array[Byte]] =
        JsonSerializer[F].serialize[ElpacaUpdate](update)

      override def deserializeUpdate(
        bytes: Array[Byte]
      ): F[Either[Throwable, ElpacaUpdate]] =
        JsonSerializer[F].deserialize[ElpacaUpdate](bytes)

      override def getCalculatedState(implicit context: L1NodeContext[F]): F[(SnapshotOrdinal, ElpacaCalculatedState)] =
        calculatedStateService.get.map(calculatedState => (calculatedState.ordinal, calculatedState.state))

      override def setCalculatedState(
        ordinal: SnapshotOrdinal,
        state  : ElpacaCalculatedState
      )(implicit context: L1NodeContext[F]): F[Boolean] =
        calculatedStateService.update(ordinal, state)

      override def hashCalculatedState(
        state: ElpacaCalculatedState
      )(implicit context: L1NodeContext[F]): F[Hash] =
        calculatedStateService.hash(state)

      override def serializeCalculatedState(
        state: ElpacaCalculatedState
      ): F[Array[Byte]] =
        JsonSerializer[F].serialize[ElpacaCalculatedState](state)

      override def deserializeCalculatedState(
        bytes: Array[Byte]
      ): F[Either[Throwable, ElpacaCalculatedState]] =
        JsonSerializer[F].deserialize[ElpacaCalculatedState](bytes)
    }
  )
}
