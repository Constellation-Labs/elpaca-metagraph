package org.proof_of_attendance_metagraph.l0.custom_routes

import cats.effect.Async
import cats.syntax.flatMap._
import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import eu.timepit.refined.auto._
import org.http4s.circe.CirceEntityCodec.circeEntityEncoder
import org.http4s.dsl.Http4sDsl
import org.http4s.server.middleware.CORS
import org.http4s.{HttpRoutes, Response}
import org.proof_of_attendance_metagraph.shared_data.calculated_state.CalculatedStateService
import org.proof_of_attendance_metagraph.shared_data.types.States.ProofOfAttendanceCalculatedState
import org.tessellation.routes.internal.{InternalUrlPrefix, PublicRoutes}

case class CustomRoutes[F[_] : Async](calculatedStateService: CalculatedStateService[F]) extends Http4sDsl[F] with PublicRoutes[F] {

  @derive(encoder, decoder)
  case class CalculatedStateResponse(
    ordinal        : Long,
    calculatedState: ProofOfAttendanceCalculatedState
  )

  private def getLatestCalculatedState: F[Response[F]] = {
    calculatedStateService
      .get
      .flatMap(state => Ok(CalculatedStateResponse(state.ordinal.value.value, state.state)))
  }

  private val routes: HttpRoutes[F] = HttpRoutes.of[F] {
    case GET -> Root / "calculated-state" / "latest" => getLatestCalculatedState
  }

  val public: HttpRoutes[F] =
    CORS
      .policy
      .withAllowCredentials(false)
      .httpRoutes(routes)

  override protected def prefixPath: InternalUrlPrefix = "/"
}
