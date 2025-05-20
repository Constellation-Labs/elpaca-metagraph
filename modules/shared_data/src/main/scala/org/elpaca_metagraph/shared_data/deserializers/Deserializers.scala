package org.elpaca_metagraph.shared_data.deserializers

import io.circe.Decoder
import org.elpaca_metagraph.shared_data.types.DataUpdates.ElpacaUpdate
import org.elpaca_metagraph.shared_data.types.States.{ElpacaCalculatedState, ElpacaOnChainState}
import io.constellationnetwork.currency.dataApplication.DataUpdate
import io.constellationnetwork.currency.dataApplication.dataApplication.DataApplicationBlock
import io.constellationnetwork.json.JsonSerializer
import io.constellationnetwork.security.signature.Signed

object Deserializers {
  private def deserialize[F[_] : JsonSerializer, A: Decoder](
    bytes: Array[Byte]
  ): F[Either[Throwable, A]] = {
    JsonSerializer[F].deserialize[A](bytes)
  }

  def deserializeUpdate[F[_] : JsonSerializer](
    bytes: Array[Byte]
  ): F[Either[Throwable, ElpacaUpdate]] =
    deserialize[F, ElpacaUpdate](bytes)

  def deserializeState[F[_] : JsonSerializer](
    bytes: Array[Byte]
  ): F[Either[Throwable, ElpacaOnChainState]] =
    deserialize[F, ElpacaOnChainState](bytes)

  def deserializeBlock[F[_] : JsonSerializer](
    bytes: Array[Byte]
  )(implicit e: Decoder[DataUpdate]): F[Either[Throwable, Signed[DataApplicationBlock]]] =
    deserialize[F, Signed[DataApplicationBlock]](bytes)

  def deserializeCalculatedState[F[_] : JsonSerializer](
    bytes: Array[Byte]
  ): F[Either[Throwable, ElpacaCalculatedState]] =
    deserialize[F, ElpacaCalculatedState](bytes)
}