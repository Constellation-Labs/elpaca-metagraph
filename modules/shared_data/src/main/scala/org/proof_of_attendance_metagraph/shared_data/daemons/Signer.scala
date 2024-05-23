package org.proof_of_attendance_metagraph.shared_data.daemons

import cats.data.NonEmptySet
import cats.effect.Async
import cats.syntax.applicativeError.catsSyntaxApplicativeError
import cats.syntax.flatMap._
import cats.syntax.functor._
import org.proof_of_attendance_metagraph.shared_data.types.DataUpdates.ProofOfAttendanceUpdate
import org.proof_of_attendance_metagraph.shared_data.types.codecs.JsonBinaryCodec
import org.tessellation.json.JsonSerializer
import org.tessellation.security.SecurityProvider
import org.tessellation.security.hash.Hash
import org.tessellation.security.signature.Signed
import org.tessellation.security.signature.signature.SignatureProof
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.security.KeyPair

trait Signer[F[_]] {
  def signAndPublish(update: ProofOfAttendanceUpdate): F[Boolean]
}

object Signer {
  def make[F[_] : Async : SecurityProvider : JsonSerializer](
    keypair  : KeyPair,
    publisher: Publisher[F]
  ): Signer[F] =
    new Signer[F] {
      private val logger: SelfAwareStructuredLogger[F] = Slf4jLogger.getLoggerFromClass(Publisher.getClass)

      override def signAndPublish(update: ProofOfAttendanceUpdate): F[Boolean] = for {
        signedUpdate <- signUpdate(update)
        result <- publisher.submitToTarget(signedUpdate).handleErrorWith(err => {
          logger.error(s"Error when submitting update: ${err.getMessage}")
            .as(false)
        })
      } yield result

      private def signUpdate(update: ProofOfAttendanceUpdate): F[Signed[ProofOfAttendanceUpdate]] = {
        JsonBinaryCodec[F].serialize[ProofOfAttendanceUpdate](update).flatMap { bytes =>
          val hash = Hash.fromBytes(bytes)
          SignatureProof.fromHash[F](keypair, hash).map(r => Signed(update, NonEmptySet.one(r)))
        }
      }
    }
}