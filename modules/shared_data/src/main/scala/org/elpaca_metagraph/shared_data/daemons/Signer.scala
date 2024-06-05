package org.elpaca_metagraph.shared_data.daemons

import cats.data.NonEmptySet
import cats.effect.Async
import cats.syntax.all._
import org.elpaca_metagraph.shared_data.types.DataUpdates.ElpacaUpdate
import org.elpaca_metagraph.shared_data.types.codecs.JsonBinaryCodec
import org.tessellation.json.JsonSerializer
import org.tessellation.security.SecurityProvider
import org.tessellation.security.hash.Hash
import org.tessellation.security.signature.Signed
import org.tessellation.security.signature.signature.SignatureProof
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.security.KeyPair

trait Signer[F[_]] {
  def signAndPublish(update: ElpacaUpdate): F[Boolean]
}

object Signer {
  def make[F[_] : Async : SecurityProvider : JsonSerializer](
    keypair  : KeyPair,
    publisher: Publisher[F]
  ): Signer[F] =
    new Signer[F] {
      private val logger: SelfAwareStructuredLogger[F] = Slf4jLogger.getLoggerFromClass(Publisher.getClass)

      override def signAndPublish(update: ElpacaUpdate): F[Boolean] = for {
        signedUpdate <- signUpdate(update)
        result <- publisher.submitToTarget(signedUpdate).handleErrorWith(err => {
          logger.error(s"Error when submitting update: ${err.getMessage}")
            .as(false)
        })
      } yield result

      private def signUpdate(update: ElpacaUpdate): F[Signed[ElpacaUpdate]] = {
        JsonBinaryCodec[F].serialize[ElpacaUpdate](update).flatMap { bytes =>
          val hash = Hash.fromBytes(bytes)
          SignatureProof.fromHash[F](keypair, hash).map(r => Signed(update, NonEmptySet.one(r)))
        }
      }
    }
}
