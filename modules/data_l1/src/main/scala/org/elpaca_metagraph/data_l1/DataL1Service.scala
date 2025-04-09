package org.elpaca_metagraph.data_l1

import cats.data.NonEmptyList
import cats.effect.Async
import cats.syntax.all._
import io.circe.{Decoder, Encoder}
import org.elpaca_metagraph.shared_data.LifecycleSharedFunctions
import org.elpaca_metagraph.shared_data.calculated_state.CalculatedStateService
import org.elpaca_metagraph.shared_data.deserializers.Deserializers
import org.elpaca_metagraph.shared_data.serializers.Serializers
import org.elpaca_metagraph.shared_data.types.DataUpdates._
import org.elpaca_metagraph.shared_data.types.States._
import org.elpaca_metagraph.shared_data.types.codecs.DataUpdateCodec._
import org.elpaca_metagraph.shared_data.validations.Errors.valid
import org.http4s.circe.CirceEntityCodec.circeEntityDecoder
import org.http4s.{EntityDecoder, HttpRoutes}
import io.constellationnetwork.currency.dataApplication._
import io.constellationnetwork.currency.dataApplication.dataApplication.{DataApplicationBlock, DataApplicationValidationErrorOr}
import io.constellationnetwork.json.JsonSerializer
import io.constellationnetwork.schema.SnapshotOrdinal
import io.constellationnetwork.security.hash.Hash
import io.constellationnetwork.security.signature.Signed

object DataL1Service {

  def make[F[+_] : Async : JsonSerializer](): BaseDataApplicationL1Service[F] =
    makeBaseDataApplicationL1Service

  private def makeBaseDataApplicationL1Service[F[+_] : Async : JsonSerializer]: BaseDataApplicationL1Service[F] = BaseDataApplicationL1Service(
    new DataApplicationL1Service[F, ElpacaUpdate, ElpacaOnChainState, ElpacaCalculatedState] {
      override def validateUpdate(
        update: ElpacaUpdate
      )(implicit context: L1NodeContext[F]): F[DataApplicationValidationErrorOr[Unit]] =
        LifecycleSharedFunctions.validateUpdate(update)

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
        Serializers.serializeBlock(block)

      override def deserializeBlock(
        bytes: Array[Byte]
      ): F[Either[Throwable, Signed[DataApplicationBlock]]] =
        Deserializers.deserializeBlock(bytes)

      override def serializeState(
        state: ElpacaOnChainState
      ): F[Array[Byte]] =
        Serializers.serializeState(state)

      override def deserializeState(
        bytes: Array[Byte]
      ): F[Either[Throwable, ElpacaOnChainState]] =
        Deserializers.deserializeState(bytes)

      override def serializeUpdate(
        update: ElpacaUpdate
      ): F[Array[Byte]] =
        Serializers.serializeUpdate(update)

      override def deserializeUpdate(
        bytes: Array[Byte]
      ): F[Either[Throwable, ElpacaUpdate]] =
        Deserializers.deserializeUpdate(bytes)

      override def serializeCalculatedState(
        state: ElpacaCalculatedState
      ): F[Array[Byte]] =
        Serializers.serializeCalculatedState(state)

      override def deserializeCalculatedState(
        bytes: Array[Byte]
      ): F[Either[Throwable, ElpacaCalculatedState]] =
        Deserializers.deserializeCalculatedState(bytes)
    }
  )
}
