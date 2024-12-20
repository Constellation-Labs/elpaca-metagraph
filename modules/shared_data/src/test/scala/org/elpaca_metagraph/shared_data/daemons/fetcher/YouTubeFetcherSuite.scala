package org.elpaca_metagraph.shared_data.daemons.fetcher

import cats.effect._
import cats.effect.testkit.TestControl
import cats.syntax.all._
import eu.timepit.refined.auto._
import io.circe.syntax._
import org.elpaca_metagraph.shared_data.Utils.toTokenAmountFormat
import org.elpaca_metagraph.shared_data.app.ApplicationConfig
import org.elpaca_metagraph.shared_data.types.Refined.ApiUrl
import org.elpaca_metagraph.shared_data.types.YouTube.LatticeClient._
import org.elpaca_metagraph.shared_data.types.YouTube.YouTubeDataAPI._
import org.http4s._
import org.http4s.circe._
import org.http4s.client.Client
import org.http4s.implicits._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.tessellation.schema.address.Address
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.time.Instant
import scala.concurrent.duration._

class YouTubeFetcherSuite extends AnyFunSuite with Matchers {
  implicit val logger: SelfAwareStructuredLogger[IO] = Slf4jLogger.getLoggerFromClass(this.getClass)

  private val apiKey = "mockApiKey"
  private val baseUrl = ApiUrl.unsafeFrom("http://localhost:8080")
  private val searchInfo = ApplicationConfig.YouTubeSearchInfo("test-query", toTokenAmountFormat(50), 60, 50, 1, 24)

  private val dagAddress1 = Address("DAG56BtU1j5uCMb5f1QxZ5oxfBhpUeYucRGygfEa")
  private val dagAddress2 = Address("DAG45ZLcgmQeRHY3oV2ZJACrFUjEZwqeXKSfZc75")

  private val latticeUsersResponse = LatticeUsersApiResponse(
    data = List(
      LatticeUser("user1", Some(dagAddress1), Some(YouTubeAccount("channel1"))),
      LatticeUser("user2", None, Some(YouTubeAccount("channel2"))),
      LatticeUser("user3", Some(dagAddress2), None)
    ),
    meta = Some(LatticeUserMeta(total = 3, limit = 100, offset = 0))
  )

  private val searchResponsePage1 = SearchListResponse(
    items = List(
      VideoSummary(Id("video1"), VideoSnippetResponse("channel1", Instant.now())),
      VideoSummary(Id("video2"), VideoSnippetResponse("channel2", Instant.now()))
    ),
    pageInfo = PageInfo(2),
    nextPageToken = Some("page2")
  )

  private val searchResponsePage2 = SearchListResponse(
    items = List(
      VideoSummary(Id("video3"), VideoSnippetResponse("channel1", Instant.now()))
    ),
    pageInfo = PageInfo(1),
    nextPageToken = None
  )

  private val videoDetailsResponse = VideoListResponse(
    items = List(
      VideoResponse("video1", VideoSnippetResponse("channel1", Instant.now()), VideoStatisticsResponse(Some(500)), VideoContentDetailsResponse("PT3M")),
      VideoResponse("video2", VideoSnippetResponse("channel2", Instant.now()), VideoStatisticsResponse(Some(50)), VideoContentDetailsResponse("PT2M"))
    )
  )

  def mockClient(responses: Map[Uri, Response[IO]]): Client[IO] = Client.fromHttpApp(HttpRoutes.of[IO] {
    case req if responses.contains(req.uri) =>
      responses(req.uri).pure[IO]
  }.orNotFound)

  test("fetchLatticeUsers should return only valid users") {
    implicit val client: Client[IO] = mockClient(Map(
      Uri.unsafeFromString(baseUrl.toString()) -> Response[IO](Status.Ok).withEntity(latticeUsersResponse.asJson)
    ))

    val fetcher = new YouTubeFetcher[IO](apiKey, baseUrl)

    val result = TestControl.execute {
      for {
        users <- fetcher.fetchLatticeUsers(baseUrl)
        _ <- IO {
          users should have size 1
          users.head.primaryDagAddress shouldBe Some(dagAddress1)
          users.head.youtube.map(_.channelId) shouldBe Some("channel1")
        }
      } yield ()
    }

    result.flatMap(_.tickAll)
  }

  test("searchVideos should handle multiple pages") {
    implicit val client: Client[IO] = mockClient(Map(
      Uri.unsafeFromString(s"$baseUrl/search?pageToken=page2") -> Response[IO](Status.Ok).withEntity(searchResponsePage2.asJson),
      Uri.unsafeFromString(s"$baseUrl/search") -> Response[IO](Status.Ok).withEntity(searchResponsePage1.asJson)
    ))

    val fetcher = new YouTubeFetcher[IO](apiKey, baseUrl)

    val result = TestControl.execute {
      for {
        videos <- fetcher.searchVideos("test-query")
        _ <- IO {
          videos shouldBe Map(
            "channel1" -> List("video1", "video3"),
            "channel2" -> List("video2")
          )
        }
      } yield ()
    }

    result.flatMap(_.tickAll)
  }

  test("filterVideosByChannels should filter videos correctly") {
    implicit val mockClient: Client[IO] = Client.fromHttpApp(HttpRoutes.empty[IO].orNotFound)

    val fetcher = new YouTubeFetcher[IO](apiKey, baseUrl)
    val channelVideoMap = Map(
      "channel1" -> List("video1", "video3"),
      "channel2" -> List("video2"),
      "channel3" -> List("video4")
    )
    val allowedChannels = List("channel1", "channel2")

    val result = fetcher.filterVideosByChannels(channelVideoMap, allowedChannels)

    result should contain theSameElementsAs List("video1", "video3", "video2")
  }

  test("fetchVideoDetails should filter videos based on criteria") {
    implicit val client: Client[IO] = mockClient(Map(
      Uri.unsafeFromString(s"$baseUrl/videos") -> Response[IO](Status.Ok).withEntity(videoDetailsResponse.asJson)
    ))

    val fetcher = new YouTubeFetcher[IO](apiKey, baseUrl)

    val result = TestControl.execute {
      for {
        details <- fetcher.fetchVideoDetails(List("video1", "video2"), 120, 100)
        _ <- IO {
          details should have size 1
          details.head.id shouldBe "video1"
        }
      } yield ()
    }

    result.flatMap(_.tickAll)
  }

  test("fetchVideoUpdates should generate updates for valid users and videos") {
    implicit val client: Client[IO] = mockClient(Map(
      Uri.unsafeFromString(s"$baseUrl/videos") -> Response[IO](Status.Ok).withEntity(videoDetailsResponse.asJson)
    ))

    val fetcher = new YouTubeFetcher[IO](apiKey, baseUrl)

    val globalSearchResult = Map(
      "channel1" -> List("video1"),
      "channel2" -> List("video2")
    )

    val result = TestControl.execute {
      for {
        updates <- fetcher.fetchVideoUpdates(
          List(LatticeUser("user1", Some(dagAddress1), Some(YouTubeAccount("channel1")))),
          searchInfo,
          globalSearchResult
        )
        _ <- IO {
          updates should have size 1
          updates.head.address shouldBe dagAddress1
          updates.head.video.id shouldBe "video1"
        }
      } yield ()
    }

    result.flatMap(_.tickAll)
  }

  test("fetchLatticeUsers should handle API errors gracefully") {
    implicit val client: Client[IO] = mockClient(Map(
      Uri.unsafeFromString(baseUrl.toString()) -> Response[IO](Status.InternalServerError)
    ))

    val fetcher = new YouTubeFetcher[IO](apiKey, baseUrl)

    val result = TestControl.execute {
      fetcher.fetchLatticeUsers(baseUrl).attempt.map {
        case Left(error) => error shouldBe a[Throwable]
        case Right(_)    => fail("Expected an error but got success")
      }
    }

    result.flatMap(_.tickAll)
  }

  test("fetchLatticeUsers should handle large datasets") {
    val largeResponse = LatticeUsersApiResponse(
      data = List.fill(1000)(LatticeUser("user", Some(dagAddress1), Some(YouTubeAccount("channel1")))),
      meta = Some(LatticeUserMeta(total = 1000, limit = 100, offset = 0))
    )

    implicit val client: Client[IO] = mockClient(Map(
      Uri.unsafeFromString(baseUrl.toString()) -> Response[IO](Status.Ok).withEntity(largeResponse.asJson)
    ))

    val fetcher = new YouTubeFetcher[IO](apiKey, baseUrl)

    val result = TestControl.execute {
      fetcher.fetchLatticeUsers(baseUrl).map { users =>
        users should have size 1000
      }
    }

    result.flatMap(_.tickAll)
  }

  test("YouTubeFetcher should fail with invalid API key") {
    implicit val mockClient: Client[IO] = Client.fromHttpApp(HttpRoutes.empty[IO].orNotFound)
    val invalidApiKey = ""
    val fetcher = new YouTubeFetcher[IO](invalidApiKey, baseUrl)

    val result = TestControl.execute {
      fetcher.fetchLatticeUsers(baseUrl).attempt.map {
        case Left(error) => error shouldBe a[Throwable]
        case Right(_)    => fail("Expected an error but got success")
      }
    }

    result.flatMap(_.tickAll)
  }

  test("YouTubeFetcher should handle rate-limiting gracefully") {
    val rateLimitResponse = Response[IO](Status.TooManyRequests)

    implicit val client: Client[IO] = mockClient(Map(
      Uri.unsafeFromString(baseUrl.toString()) -> rateLimitResponse
    ))

    val fetcher = new YouTubeFetcher[IO](apiKey, baseUrl)

    val result = TestControl.execute {
      fetcher.fetchLatticeUsers(baseUrl).attempt.map {
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

    val fetcher = new YouTubeFetcher[IO](apiKey, baseUrl)

    val result = TestControl.execute {
      fetcher.fetchLatticeUsers(baseUrl).flatMap { _ =>
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

    val fetcher = new YouTubeFetcher[IO](apiKey, baseUrl)

    val result = TestControl.execute {
      fetcher.fetchLatticeUsers(baseUrl).attempt.map {
        case Left(error) => error shouldBe a[Throwable]
        case Right(_)    => fail("Expected an error but got success")
      }
    }

    result.flatMap(_.tickAll)
  }

}
