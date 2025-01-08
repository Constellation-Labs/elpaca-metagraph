package org.elpaca_metagraph.shared_data.daemons.fetcher

import cats.effect._
import cats.effect.unsafe.implicits.global
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

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
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
        VideoStatisticsResponse(Some(50)),
        VideoContentDetailsResponse("PT3M")
      ),
      VideoResponse(
        "video2",
        VideoSnippetResponse("channel2", Instant.now()),
        VideoStatisticsResponse(Some(49)),
        VideoContentDetailsResponse("PT2M")
      )
    )
  )

  private def buildVideosDetailsUri(videoIds: List[String]): Uri =
    Uri
      .unsafeFromString(s"$baseUrl/videos")
      .withQueryParam("key", apiKey)
      .withQueryParam("id", videoIds.mkString(","))
      .withQueryParam("part", "snippet,contentDetails,statistics")

  private def buildSearchUri(searchQuery: String): Uri =
    Uri
      .unsafeFromString(s"$baseUrl/search")
      .withQueryParam("key", apiKey)
      .withQueryParam("q", URLEncoder.encode(searchQuery, StandardCharsets.UTF_8))
      .withQueryParam("type", "video")
      .withQueryParam("order", "date")
      .withQueryParam("maxResults", 50)
      .withQueryParam("part", "snippet")

  test("searchVideos should handle multiple pages") {
    val searchQuery = "test-query"
    implicit val client: Client[IO] = mockClient(
      Map(
        buildSearchUri(searchQuery).withQueryParam("pageToken", "page2") -> Response[IO](Status.Ok).withEntity(searchResponsePage2.asJson),
        buildSearchUri(searchQuery) -> Response[IO](Status.Ok).withEntity(searchResponsePage1.asJson)
      )
    )

    val fetcher = new YouTubeFetcher[IO](apiKey, baseUrl)
    val videos = fetcher.searchVideos(searchQuery).unsafeRunSync()
    videos shouldBe Map(
      "channel1" -> List("video1", "video3"),
      "channel2" -> List("video2")
    )
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

  test("fetchVideoDetails should filter videos based on video duration and view count") {
    val videosIds = List("video1", "video2")

    implicit val client: Client[IO] = mockClient(Map(
      buildVideosDetailsUri(videosIds) -> Response[IO](Status.Ok).withEntity(videoDetailsResponse.asJson)
    ))

    val fetcher = new YouTubeFetcher[IO](apiKey, baseUrl)
    val details = fetcher.fetchVideoDetails(videosIds, 120, 50).unsafeRunSync()

    details should have size 1
    details.head.id shouldBe "video1"
  }

  test("fetchVideoUpdates should generate updates for valid users and videos") {
    val globalSearchResult = Map(
      "channel1" -> List("video1"),
      "channel2" -> List("video2")
    )

    implicit val client: Client[IO] = mockClient(Map(
      buildVideosDetailsUri(List("video1", "video2")) -> Response[IO](Status.Ok).withEntity(videoDetailsResponse.asJson)
    ))

    val fetcher = new YouTubeFetcher[IO](apiKey, baseUrl)

    val updates = fetcher.fetchVideoUpdates(
      List(
        LatticeUser("user1", Some(dagAddress1), Some(YouTubeAccount("channel1")), None),
        LatticeUser("user2", Some(dagAddress2), Some(YouTubeAccount("channel2")), None)
      ),
      searchInfo,
      globalSearchResult
    ).unsafeRunSync()

    updates should have size 2
    updates.head.address shouldBe dagAddress1
    updates.head.video.id shouldBe "video1"
  }

}
