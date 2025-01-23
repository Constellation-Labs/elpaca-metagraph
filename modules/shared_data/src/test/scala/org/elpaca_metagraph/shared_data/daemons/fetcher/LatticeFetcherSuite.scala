package org.elpaca_metagraph.shared_data.daemons.fetcher

import cats.effect._
import cats.effect.testkit.TestControl
import cats.effect.unsafe.implicits.global
import cats.syntax.all._
import io.circe.syntax._
import org.elpaca_metagraph.shared_data.types.Lattice._
import org.http4s._
import org.http4s.circe._
import org.http4s.client.Client
import org.http4s.implicits._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._

class LatticeFetcherSuite extends AnyFunSuite with Matchers with FetcherSuite {
  private val latticeUsers = List(
    LatticeUser("user1", Some(dagAddress1), Some(LinkedAccounts(Some(YouTubeAccount("channel1")), None)), None),
    LatticeUser("user2", None, Some(LinkedAccounts(Some(YouTubeAccount("channel2")), None)), None),
    LatticeUser("user3", Some(dagAddress1), Some(LinkedAccounts(None, Some(XAccount("account1")))), None),
    LatticeUser("user4", None, Some(LinkedAccounts(None, Some(XAccount("account2")))), None),
    LatticeUser("user5", Some(dagAddress2), Some(LinkedAccounts(None, None)), None),
    LatticeUser("user6", Some(dagAddress2), Some(LinkedAccounts(None, None)), Some(XAccount("account6")))
  )
  private val latticeUsersResponse = LatticeUsersApiResponse(
    data = latticeUsers,
    meta = Some(LatticeUserMeta(total = 6, limit = 100, offset = 0))
  )

  test("fetchLatticeUsersWithYouTubeAccount should return only valid users with YouTube accounts") {
    implicit val client: Client[IO] = mockClient(Map(
      Uri.unsafeFromString(s"$baseUrl?limit=100&offset=0") -> Response[IO](Status.Ok).withEntity(latticeUsersResponse.asJson)
    ))

    val fetcher = new LatticeFetcher[IO](baseUrl)
    val users = fetcher.fetchLatticeUsersWithYouTubeAccount().unsafeRunSync()

    users should have size 1
    users.head.primaryDagAddress shouldBe Some(dagAddress1)
    users.head.linkedAccounts.get.youtube.map(_.channelId) shouldBe Some("channel1")
  }

  test("fetchLatticeUsersWithXAccount should return only valid users with X accounts") {
    implicit val client: Client[IO] = mockClient(Map(
      Uri.unsafeFromString(s"$baseUrl?limit=100&offset=0") -> Response[IO](Status.Ok).withEntity(latticeUsersResponse.asJson)
    ))

    val fetcher = new LatticeFetcher[IO](baseUrl)
    val users = fetcher.fetchLatticeUsersWithXAccount().unsafeRunSync()

    users should have size 2
    users.head.primaryDagAddress shouldBe Some(dagAddress1)
    users.head.linkedAccounts.get.twitter.map(_.username) shouldBe Some("account1")
    // Should now also support previous response format from Lattice
    users.last.twitter.map(_.username) shouldBe Some("account6")
  }

  test("fetchLatticeUsers should handle API errors gracefully") {
    implicit val client: Client[IO] = mockClient(Map(
      Uri.unsafeFromString(baseUrl.toString()) -> Response[IO](Status.InternalServerError)
    ))

    val fetcher = new LatticeFetcher[IO](baseUrl)

    val result = TestControl.execute {
      fetcher.fetchLatticeUsers().attempt.map {
        case Left(error) => error shouldBe a[Throwable]
        case Right(_)    => fail("Expected an error but got success")
      }
    }

    result.flatMap(_.tickAll)
  }

  test("fetchLatticeUsers should handle pagination correctly") {
    val pageSize = 100
    val totalUsers = 1000
    val totalPages = totalUsers / pageSize

    val paginatedResponses = (0 until totalPages).map { page =>
      val offset = page * pageSize
      val response = LatticeUsersApiResponse(
        data = List
          .fill(pageSize)(
            LatticeUser(s"user$offset", Some(dagAddress1), Some(LinkedAccounts(Some(YouTubeAccount(s"channel$offset")), None)), None)
          ),
        meta = Some(LatticeUserMeta(total = totalUsers.toLong, limit = pageSize.toLong, offset = offset.toLong))
      )

      Uri
        .unsafeFromString(baseUrl.toString())
        .withQueryParam("limit", pageSize)
        .withQueryParam("offset", offset) -> Response[IO](Status.Ok)
        .withEntity(response.asJson)
    }.toMap

    implicit val client: Client[IO] = mockClient(paginatedResponses)

    val fetcher = new LatticeFetcher[IO](baseUrl)
    val users = fetcher.fetchLatticeUsers().unsafeRunSync()

    users should have size totalUsers.toLong
  }

  test("LatticeFetcher should handle API timeouts") {
    val delayedResponse = IO.sleep(5.seconds) *> Response[IO](Status.Ok).withEntity(latticeUsersResponse.asJson).pure[IO]

    implicit val client: Client[IO] = Client.fromHttpApp(HttpRoutes.of[IO] {
      case _ => delayedResponse
    }.orNotFound)

    val fetcher = new LatticeFetcher[IO](baseUrl)

    val result = TestControl.execute {
      fetcher.fetchLatticeUsers().attempt.map {
        case Left(error) => error shouldBe a[Throwable]
        case Right(_)    => fail("Expected an error but got success")
      }
    }

    result.flatMap(_.tickAll)
  }

}
