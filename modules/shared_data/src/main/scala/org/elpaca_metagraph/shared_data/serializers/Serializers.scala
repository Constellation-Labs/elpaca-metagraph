package org.elpaca_metagraph.shared_data.serializers

import cats.effect.Async
import io.circe.Encoder
import io.circe.syntax.EncoderOps
import org.elpaca_metagraph.shared_data.types.DataUpdates.{ElpacaUpdate, StreakUpdate}
import org.elpaca_metagraph.shared_data.types.States.{ElpacaCalculatedState, ElpacaOnChainState}
import io.constellationnetwork.currency.dataApplication.DataUpdate
import io.constellationnetwork.currency.dataApplication.dataApplication.DataApplicationBlock
import io.constellationnetwork.json.JsonSerializer
import io.constellationnetwork.security.signature.Signed

import java.nio.charset.StandardCharsets
import java.util.Base64

object Serializers {
  private def serialize[F[_] : JsonSerializer, A: Encoder](
    serializableData: A
  ): F[Array[Byte]] = {
    JsonSerializer[F].serialize[A](serializableData)
  }

  def serializeUpdate[F[_] : Async : JsonSerializer](
    update: ElpacaUpdate
  ): F[Array[Byte]] =
    update match {
      case _: StreakUpdate =>
        Async[F].delay {
          val dataSignPrefix = "\u0019Constellation Signed Data:\n"
          val encodedString = Base64.getEncoder.encodeToString(update.asJson.deepDropNullValues.noSpaces.getBytes(StandardCharsets.UTF_8))
          val completeString = s"$dataSignPrefix${encodedString.length}\n$encodedString"

          completeString.getBytes(StandardCharsets.UTF_8)
        }
      case _ =>
        serialize[F, ElpacaUpdate](update)
    }


  def serializeState[F[_] : JsonSerializer](
    state: ElpacaOnChainState
  ): F[Array[Byte]] =
    serialize[F, ElpacaOnChainState](state)

  def serializeBlock[F[_] : JsonSerializer](
    state: Signed[DataApplicationBlock]
  )(implicit e: Encoder[DataUpdate]): F[Array[Byte]] =
    serialize[F, Signed[DataApplicationBlock]](state)

  def serializeCalculatedState[F[_] : JsonSerializer](
    state: ElpacaCalculatedState
  ): F[Array[Byte]] =
    serialize[F, ElpacaCalculatedState](state)
}