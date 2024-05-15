package org.proof_of_attendance_metagraph.data_l1.daemons

import cats.Applicative
import cats.effect.Async
import cats.syntax.flatMap._
import cats.syntax.functor._
import org.http4s.Method.POST
import org.http4s.Status
import org.http4s.circe.CirceEntityCodec.{circeEntityDecoder, circeEntityEncoder}
import org.http4s.client.Client
import org.proof_of_attendance_metagraph.shared_data.types.DataUpdates.ProofOfAttendanceUpdate
import org.tessellation.node.shared.http.p2p.PeerResponse
import org.tessellation.node.shared.http.p2p.PeerResponse.PeerResponse
import org.tessellation.schema.peer.P2PContext
import org.tessellation.security.signature.Signed
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

trait Publisher[F[_]] {
  val target: P2PContext

  def submitToTarget(update: Signed[ProofOfAttendanceUpdate]): F[Boolean]
}

object Publisher {

  def make[F[_] : Async](localClient: Client[F], remoteTarget: P2PContext): Publisher[F] =
    new Publisher[F] {
      private val logger: SelfAwareStructuredLogger[F] = Slf4jLogger.getLoggerFromClass(Publisher.getClass)

      override val target: P2PContext = remoteTarget

      override def submitToTarget(update: Signed[ProofOfAttendanceUpdate]): F[Boolean] = submit(update).run(target)

      private def submit(update: Signed[ProofOfAttendanceUpdate]): PeerResponse[F, Boolean] =
        PeerResponse(s"data", POST)(localClient) { (req, c) =>
          c.run(req.withEntity(update)).use {
            case Status.Successful(_) => Applicative[F].pure(true)
            case res =>
              res.as[String].flatTap(msg => logger.warn(s"Failed to submit update with exception: $msg")).as(false)
          }
        }
    }
}
