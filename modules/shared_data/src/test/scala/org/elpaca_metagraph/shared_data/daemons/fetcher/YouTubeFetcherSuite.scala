package org.elpaca_metagraph.shared_data.daemons.fetcher

import cats.effect._
import cats.effect.testkit.TestControl
import io.circe.syntax._
import org.elpaca_metagraph.shared_data.Utils.toTokenAmountFormat
import org.elpaca_metagraph.shared_data.app.ApplicationConfig
import org.elpaca_metagraph.shared_data.types.Lattice._
import org.elpaca_metagraph.shared_data.types.YouTube.YouTubeDataAPI._
import org.http4s._
import org.http4s.circe._
import org.http4s.client.Client
import org.http4s.implicits._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.time.Instant

class YouTubeFetcherSuite extends AnyFunSuite with Matchers with FetcherSuite {
  private val apiKey = "mockApiKey"
  private val searchInfo = ApplicationConfig.YouTubeSearchInfo("test-query", toTokenAmountFormat(50), 60, 50, 1, 24)

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
      VideoResponse(
        "video1",
        VideoSnippetResponse("channel1", Instant.now()),
        VideoStatisticsResponse(Some(500)),
        VideoContentDetailsResponse("PT3M")
      ),
      VideoResponse(
        "video2",
        VideoSnippetResponse("channel2", Instant.now()),
        VideoStatisticsResponse(Some(50)),
        VideoContentDetailsResponse("PT2M")
      )
    )
  )

  test("searchVideos should handle multiple pages") {
    implicit val client: Client[IO] = mockClient(
      Map(
        Uri.unsafeFromString(s"$baseUrl/search?pageToken=page2") -> Response[IO](Status.Ok).withEntity(searchResponsePage2.asJson),
        Uri.unsafeFromString(s"$baseUrl/search") -> Response[IO](Status.Ok).withEntity(searchResponsePage1.asJson)
      )
    )

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
    implicit val client: Client[IO] = mockClient(
      Map(
        Uri.unsafeFromString(s"$baseUrl/videos") -> Response[IO](Status.Ok).withEntity(videoDetailsResponse.asJson)
      )
    )

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
    implicit val client: Client[IO] = mockClient(
      Map(
        Uri.unsafeFromString(s"$baseUrl/videos") -> Response[IO](Status.Ok).withEntity(videoDetailsResponse.asJson)
      )
    )

    val fetcher = new YouTubeFetcher[IO](apiKey, baseUrl)

    val globalSearchResult = Map(
      "channel1" -> List("video1"),
      "channel2" -> List("video2")
    )

    val result = TestControl.execute {
      for {
        updates <- fetcher.fetchVideoUpdates(
          List(LatticeUser("user1", Some(dagAddress1), Some(YouTubeAccount("channel1")), None)),
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

}
