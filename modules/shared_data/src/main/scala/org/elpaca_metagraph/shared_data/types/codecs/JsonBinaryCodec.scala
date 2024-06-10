package org.elpaca_metagraph.shared_data.types.codecs

import cats.effect.Sync
import io.circe.jawn.JawnParser
import io.circe.syntax.EncoderOps
import io.circe.{Decoder, Encoder, Printer}
import org.tessellation.json.JsonHashSerializer

object JsonBinaryCodec {

  def apply[F[_] : JsonHashSerializer]: JsonHashSerializer[F] = implicitly

  def forSync[F[_] : Sync]: F[JsonHashSerializer[F]] = {
    def printer = Printer(dropNullValues = true, indent = "", sortKeys = true)

    forSync[F](printer)
  }

  def forSync[F[_] : Sync](printer: Printer): F[JsonHashSerializer[F]] =
    Sync[F].delay {
      new JsonHashSerializer[F] {
        override def serialize[A: Encoder](content: A): F[Array[Byte]] =
          Sync[F].delay(content.asJson.printWith(printer).getBytes("UTF-8"))


        override def deserialize[A: Decoder](content: Array[Byte]): F[Either[Throwable, A]] =
          Sync[F].delay(JawnParser(false).decodeByteArray[A](content))
      }
    }
}
