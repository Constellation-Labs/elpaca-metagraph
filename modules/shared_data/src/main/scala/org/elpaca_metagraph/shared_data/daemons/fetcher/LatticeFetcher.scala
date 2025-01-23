package org.elpaca_metagraph.shared_data.daemons.fetcher

import cats.effect.Async
import cats.syntax.all._
import fs2.io.net.Network
import org.elpaca_metagraph.shared_data.types.Lattice._
import org.elpaca_metagraph.shared_data.types.Refined.ApiUrl
import org.http4s.circe.jsonOf
import org.http4s.client.Client
import org.http4s.{Method, Request, Uri}
import org.typelevel.log4cats.SelfAwareStructuredLogger

class LatticeFetcher[F[_]: Async: Network](
  apiUrl: ApiUrl
)(implicit client: Client[F],
  logger: SelfAwareStructuredLogger[F]) {

  def fetchLatticeUsers(
    offset: Long = 0,
    users : List[LatticeUser] = List.empty
  ): F[List[LatticeUser]] = {
    val usersPerRequest = 100
    val request = Request[F](
      Method.GET,
      Uri
        .unsafeFromString(apiUrl.toString())
        .withQueryParam("limit", usersPerRequest)
        .withQueryParam("offset", offset)
    )

    for {
      _ <- logger.info(s"Fetching Lattice users from $apiUrl with offset $offset")
      response <- client.expect[LatticeUsersApiResponse](request)(jsonOf[F, LatticeUsersApiResponse]).handleErrorWith { e =>
        logger.error(e)(s"Error fetching Lattice users: ${e.getMessage}").as(LatticeUsersApiResponse(List.empty, None))
      }
      newUsers = users ++ response.data
      latticeUsers <-
        if (!response.meta.exists(meta => meta.offset + meta.limit < meta.total)) {
          val result = newUsers.filter(_.primaryDagAddress.isDefined)
          logger.info(s"Found ${result.length} Lattice users").as(result)
        } else fetchLatticeUsers(response.meta.get.offset + response.meta.get.limit, newUsers)
    } yield latticeUsers
  }

  def fetchLatticeUsersWithYouTubeAccount(
    offset: Long = 0
  ): F[List[LatticeUser]] = for {
    users <- fetchLatticeUsers(offset).map(_.filter(_.linkedAccounts.exists(_.youtube.isDefined)))
    _ <- logger.info(s"Found ${users.length} Lattice users with YouTube account")
  } yield users

  def fetchLatticeUsersWithXAccount(
    offset: Long = 0
  ): F[List[LatticeUser]] = for {
    users <- fetchLatticeUsers(offset).map(_.filter(user => user.twitter.isDefined || user.linkedAccounts.exists(_.twitter.isDefined)))
    _ <- logger.info(s"Found ${users.length} Lattice users with X account")
  } yield users
}
