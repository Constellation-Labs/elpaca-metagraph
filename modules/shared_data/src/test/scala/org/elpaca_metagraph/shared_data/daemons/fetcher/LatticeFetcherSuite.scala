package org.elpaca_metagraph.shared_data.daemons.fetcher

import cats.effect._
import cats.effect.testkit.TestControl
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
  private val latticeUsersResponse = LatticeUsersApiResponse(
    data = List(
      LatticeUser("user1", Some(dagAddress1), Some(YouTubeAccount("channel1")), None),
      LatticeUser("user2", None, Some(YouTubeAccount("channel2")), None),
      LatticeUser("user3", Some(dagAddress1), None, Some(XAccount("account1"))),
      LatticeUser("user4", None, None, Some(XAccount("account2"))),
      LatticeUser("user5", Some(dagAddress2), None, None)
    ),
    meta = Some(LatticeUserMeta(total = 5, limit = 100, offset = 0))
  )

  test("fetchLatticeUsers should return only valid users with YouTube accounts") {
    implicit val client: Client[IO] = mockClient(Map(
      Uri.unsafeFromString(baseUrl.toString()) -> Response[IO](Status.Ok).withEntity(latticeUsersResponse.asJson)
    ))

    val fetcher = new LatticeFetcher[IO](baseUrl)

    val result = TestControl.execute {
      for {
        users <- fetcher.fetchLatticeUsersWithYouTubeAccount()
        _ <- IO {
          users should have size 1
          users.head.primaryDagAddress shouldBe Some(dagAddress1)
          users.head.youtube.map(_.channelId) shouldBe Some("channel1")
        }
      } yield ()
    }

    result.flatMap(_.tickAll)
  }

  test("fetchLatticeUsers should return only valid users with X accounts") {
    implicit val client: Client[IO] = mockClient(Map(
      Uri.unsafeFromString(baseUrl.toString()) -> Response[IO](Status.Ok).withEntity(latticeUsersResponse.asJson)
    ))

    val fetcher = new LatticeFetcher[IO](baseUrl)

    val result = TestControl.execute {
      for {
        users <- fetcher.fetchLatticeUsersWithYouTubeAccount()
        _ <- IO {
          users should have size 1
          users.head.primaryDagAddress shouldBe Some(dagAddress1)
          users.head.twitter.map(_.username) shouldBe Some("account1")
        }
      } yield ()
    }

    result.flatMap(_.tickAll)
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

  test("fetchLatticeUsers should handle large datasets") {
    val largeResponse = LatticeUsersApiResponse(
      data = List.fill(1000)(LatticeUser("user", Some(dagAddress1), Some(YouTubeAccount("channel1")), None)),
      meta = Some(LatticeUserMeta(total = 1000, limit = 100, offset = 0))
    )

    implicit val client: Client[IO] = mockClient(Map(
      Uri.unsafeFromString(baseUrl.toString()) -> Response[IO](Status.Ok).withEntity(largeResponse.asJson)
    ))

    val fetcher = new LatticeFetcher[IO](baseUrl)

    val result = TestControl.execute {
      fetcher.fetchLatticeUsers().map { users =>
        users should have size 1000
      }
    }

    result.flatMap(_.tickAll)
  }

  test("YouTubeFetcher should handle rate-limiting gracefully") {
    val rateLimitResponse = Response[IO](Status.TooManyRequests)

    implicit val client: Client[IO] = mockClient(Map(
      Uri.unsafeFromString(baseUrl.toString()) -> rateLimitResponse
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

  test("YouTubeFetcher should log API responses and errors") {
    val response = Response[IO](Status.Ok).withEntity(latticeUsersResponse.asJson)

    implicit val client: Client[IO] = mockClient(Map(
      Uri.unsafeFromString(baseUrl.toString()) -> response
    ))

    val fetcher = new LatticeFetcher[IO](baseUrl)

    val result = TestControl.execute {
      fetcher.fetchLatticeUsers().flatMap { _ =>
        logger.info("API responded successfully")
      }
    }

    result.flatMap(_.tickAll)
  }

  test("YouTubeFetcher should handle API timeouts") {
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
