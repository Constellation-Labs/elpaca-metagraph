package org.proof_of_attendance_metagraph.shared_data.types.codecs

import cats.effect.Sync
import cats.syntax.functor._
import io.circe.jawn.JawnParser
import io.circe.syntax.EncoderOps
import io.circe.{Decoder, Encoder, Printer}
import org.tessellation.json.JsonSerializer

object JsonBinaryCodec {

  def apply[F[_] : JsonSerializer]: JsonSerializer[F] = implicitly

  def forSync[F[_] : Sync]: F[JsonSerializer[F]] = {
    def printer = Printer(dropNullValues = true, indent = "", sortKeys = true)

    forSync[F](printer)
  }

  def forSync[F[_] : Sync](printer: Printer): F[JsonSerializer[F]] =
    Sync[F].delay {
      new JsonSerializer[F] {
        override def serialize[A: Encoder](content: A): F[Array[Byte]] =
          Sync[F].delay(content.asJson.printWith(printer).getBytes("UTF-8"))


        override def deserialize[A: Decoder](content: Array[Byte]): F[Either[Throwable, A]] =
          Sync[F]
            .delay(content)
            .map(JawnParser(false).decodeByteArray[A](_))
      }
    }
}
