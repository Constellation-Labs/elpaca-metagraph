package org.elpaca_metagraph.shared_data.daemons.fetcher

import cats.effect._
import cats.effect.unsafe.implicits.global
import cats.syntax.all._
import eu.timepit.refined.types.numeric.NonNegLong
import io.circe.syntax._
import org.elpaca_metagraph.shared_data.Utils.toTokenAmountFormat
import org.elpaca_metagraph.shared_data.app.ApplicationConfig
import org.elpaca_metagraph.shared_data.app.ApplicationConfig.YouTubeSearchInfo
import org.elpaca_metagraph.shared_data.types.Lattice._
import org.elpaca_metagraph.shared_data.types.States.YouTubeDataSource
import org.elpaca_metagraph.shared_data.types.YouTube.YouTubeDataAPI._
import org.elpaca_metagraph.shared_data.types.YouTube._
import org.http4s._
import org.http4s.circe._
import org.http4s.client.Client
import org.http4s.implicits._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import io.constellationnetwork.schema.balance.Amount
import io.constellationnetwork.schema.epoch.EpochProgress

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import scala.collection.immutable.ListMap
import scala.concurrent.duration._

class YouTubeFetcherSuite extends AnyFunSuite with Matchers with FetcherSuite {
  private val apiKey = "mockApiKey"
  private val testQuery = "test-query"
  private val searchInfo = YouTubeSearchInfo(
    text = testQuery,
    rewardAmount = toTokenAmountFormat(50),
    maxPerDay = 1,
    minimumDuration = 60.seconds,
    minimumViews = 50,
    publishedWithinHours = 3.hours,
    daysToMonitorVideoUpdates = 30.days
  )

  private val searchResponsePage1 = SearchListResponse(
    items = List(
      VideoSummary(Id("video1"), VideoSnippetResponse("channel1", Instant.now())),
      VideoSummary(Id("video2"), VideoSnippetResponse("channel2", Instant.now()))
    ),
    pageInfo = PageInfo(2),
    nextPageToken = None
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
        VideoContentDetailsResponse("PT1M")
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

  test("searchVideos should return relevant videos for the provided search term") {
    implicit val client: Client[IO] = mockClient(Map(
      buildSearchUri(testQuery) -> Response[IO](Status.Ok).withEntity(searchResponsePage1.asJson)
    ))

    val fetcher = new YouTubeFetcher[IO](apiKey, baseUrl)
    val videos = fetcher.searchVideos(testQuery).unsafeRunSync()
    videos shouldBe Map("channel1" -> List("video1"), "channel2" -> List("video2"))
  }

  test("searchVideos handling a single result") {
    implicit val client: Client[IO] = mockClient(Map(
      buildSearchUri(testQuery) -> Response[IO](Status.Ok).withEntity(searchResponsePage2.asJson)
    ))

    val fetcher = new YouTubeFetcher[IO](apiKey, baseUrl)
    val videos = fetcher.searchVideos(testQuery).unsafeRunSync()
    videos shouldBe Map("channel1" -> List("video3"))
  }

  test("searchVideos should handle multiple pages, correctly grouping results by channel") {
    implicit val client: Client[IO] = mockClient(
      Map(
        buildSearchUri(testQuery)
          -> Response[IO](Status.Ok).withEntity(searchResponsePage1.copy(pageInfo = PageInfo(3), nextPageToken = Some("page2")).asJson),

        buildSearchUri(testQuery).withQueryParam("pageToken", "page2")
          -> Response[IO](Status.Ok).withEntity(searchResponsePage2.copy(pageInfo = PageInfo(3)).asJson)
      )
    )

    val fetcher = new YouTubeFetcher[IO](apiKey, baseUrl)
    val videos = fetcher.searchVideos(testQuery).unsafeRunSync()
    videos shouldBe Map(
      "channel1" -> List("video1", "video3"),
      "channel2" -> List("video2")
    )
  }

  test("filterVideosByChannels should filter videos correctly") {
    implicit val mockClient: Client[IO] = Client.fromHttpApp(HttpRoutes.empty[IO].orNotFound)
    val fetcher = new YouTubeFetcher[IO](apiKey, baseUrl)
    val videos = Map(
      "channel1" -> List("video1", "video3"),
      "channel2" -> List("video2"),
      "channel3" -> List("video4") // no lattice user for channel3 - should be skipped
    )
    val channelsFromValidLatticeUsers = List("channel1", "channel2")
    val result = fetcher.filterVideosByChannels(videos, channelsFromValidLatticeUsers)

    result should contain theSameElementsAs List("video1", "video3", "video2")
  }

  test("fetchVideoDetails should filter videos based on video duration") {
    // video1 is 3 minutes long, video2 is 1 minute long
    val videosIds = List("video1", "video2")

    implicit val client: Client[IO] = mockClient(Map(
      buildVideosDetailsUri(videosIds) -> Response[IO](Status.Ok).withEntity(videoDetailsResponse.asJson)
    ))

    val fetcher = new YouTubeFetcher[IO](apiKey, baseUrl)
    // return all videos with at least 2 minutes long
    val details = fetcher.fetchVideoDetails(videosIds, 120).unsafeRunSync()

    details should have size 1
    details.head.id shouldBe "video1"
  }

  test("fetchVideoUpdates should generate updates for valid users and videos") {
    implicit val client: Client[IO] = mockClient(Map(
      buildVideosDetailsUri(List("video1", "video2")) -> Response[IO](Status.Ok).withEntity(videoDetailsResponse.asJson)
    ))

    val fetcher = new YouTubeFetcher[IO](apiKey, baseUrl)
    val updates = fetcher.fetchVideoUpdates(
      List(
        LatticeUser("user1", Some(dagAddress1), Some(LinkedAccounts(Some(YouTubeAccount("channel1")), None)), None),
        LatticeUser("user2", Some(dagAddress2), Some(LinkedAccounts(Some(YouTubeAccount("channel2")), None)), None)
      ),
      List("video1", "video2"),
      searchInfo.copy(minimumDuration = 2.minutes)
    ).unsafeRunSync()

    updates should have size 1 // video 2 doesn't meet the minimum duration requirement
    updates.head.address shouldBe dagAddress1
    updates.head.video.id shouldBe "video1"
  }

  test("filterLatticeUsers should exclude users with videos already rewarded") {
    val rewardedVideo = Some(VideoDetails("video1", "channel1", Instant.now(), 1000, 180))
    val rewardInfo = YouTubeRewardInfo(
      dailyEpochProgress = EpochProgress(NonNegLong(1)),
      epochProgressToReward = EpochProgress(NonNegLong(1)),
      amountToReward = toTokenAmountFormat(50),
      searchText = testQuery,
      dailyPostsNumber = 1,
      videos = List(rewardedVideo.get)
    )

    val dataSource = YouTubeDataSource(ListMap(dagAddress1 -> YouTubeDataSourceAddress(ListMap(testQuery -> rewardInfo))))

    val latticeUsers = List(
      LatticeUser("user1", Some(dagAddress1), Some(LinkedAccounts(Some(YouTubeAccount("channel1")), None)), None)
    )

    val result = YouTubeFetcher.filterLatticeUsers(
      latticeUsers,
      List(searchInfo),
      dataSource,
      rewardedVideo
    )

    result shouldBe empty
  }

  test("filterLatticeUsers should include users with videos not yet rewarded and under daily limit") {
    val unrewardedVideo = VideoDetails("video2", "channel1", Instant.now(), 900, 120)
    val rewardInfo = YouTubeRewardInfo(
      dailyEpochProgress = EpochProgress(NonNegLong(0)),
      epochProgressToReward = EpochProgress(NonNegLong(1)),
      amountToReward = toTokenAmountFormat(50),
      searchText = testQuery,
      dailyPostsNumber = 0,
      videos = List.empty
    )
    val dataSource = YouTubeDataSource(ListMap(dagAddress1 -> YouTubeDataSourceAddress(ListMap(testQuery -> rewardInfo))))

    val latticeUsers = List(
      LatticeUser("user1", Some(dagAddress1), Some(LinkedAccounts(Some(YouTubeAccount("channel1")), None)), None)
    )

    val result = YouTubeFetcher.filterLatticeUsers(
      latticeUsers,
      List(searchInfo),
      dataSource,
      Some(unrewardedVideo)
    )

    result should have size 1
    result.head.primaryDagAddress shouldBe Some(dagAddress1)
  }

  test("filterLatticeUsers should exclude users exceeding daily limit of one reward") {
    val unrewardedVideo = VideoDetails("video2", "channel1", Instant.now(), 900, 120)
    val rewardInfo = YouTubeRewardInfo(
      dailyEpochProgress = EpochProgress(NonNegLong(1)),
      epochProgressToReward = EpochProgress(NonNegLong(1)),
      amountToReward = toTokenAmountFormat(50),
      searchText = testQuery,
      dailyPostsNumber = 1,
      videos = List(unrewardedVideo)
    )

    val dataSource = YouTubeDataSource(ListMap(dagAddress1 -> YouTubeDataSourceAddress(ListMap(testQuery -> rewardInfo))))

    val latticeUsers = List(
      LatticeUser("user1", Some(dagAddress1), Some(LinkedAccounts(Some(YouTubeAccount("channel1")), None)), None)
    )

    val result = YouTubeFetcher.filterLatticeUsers(
      latticeUsers,
      List(searchInfo),
      dataSource,
      Some(unrewardedVideo)
    )

    result shouldBe empty
  }

  test("filterLatticeUsers should not reward twice the same video") {
    val rewardedVideo = VideoDetails("video2", "channel1", Instant.now(), 900, 120)
    val rewardInfo = YouTubeRewardInfo(
      dailyEpochProgress = EpochProgress(NonNegLong(1)),
      epochProgressToReward = EpochProgress(NonNegLong(1)),
      amountToReward = toTokenAmountFormat(50),
      searchText = testQuery,
      dailyPostsNumber = 0,
      videos = List(rewardedVideo)
    )

    val dataSource = YouTubeDataSource(ListMap(dagAddress1 -> YouTubeDataSourceAddress(ListMap(testQuery -> rewardInfo))))

    val latticeUsers = List(
      LatticeUser("user1", Some(dagAddress1), Some(LinkedAccounts(Some(YouTubeAccount("channel1")), None)), None)
    )

    val result = YouTubeFetcher.filterLatticeUsers(
      latticeUsers,
      List(searchInfo),
      dataSource,
      Some(rewardedVideo)
    )

    result shouldBe empty
  }

  test("filterLatticeUsers should handle multiple users with mixed daily limits") {
    val dataSource = YouTubeDataSource(
      ListMap(
        dagAddress1 -> YouTubeDataSourceAddress(
          ListMap(testQuery -> YouTubeRewardInfo(
            dailyEpochProgress = EpochProgress(NonNegLong(1)),
            epochProgressToReward = EpochProgress(NonNegLong(1)),
            amountToReward = toTokenAmountFormat(50),
            searchText = testQuery,
            dailyPostsNumber = 1,
            videos = List(VideoDetails("video1", "channel1", Instant.now(), 1000, 180))
          ))
        ),
        dagAddress2 -> YouTubeDataSourceAddress(
          ListMap(testQuery -> YouTubeRewardInfo(
            dailyEpochProgress = EpochProgress(NonNegLong(1)),
            epochProgressToReward = EpochProgress(NonNegLong(1)),
            amountToReward = toTokenAmountFormat(50),
            searchText = testQuery,
            dailyPostsNumber = 0,
            videos = List(VideoDetails("video2", "channel2", Instant.now(), 1000, 180))
          ))
        ),
        dagAddress3 -> YouTubeDataSourceAddress(
          ListMap(testQuery -> YouTubeRewardInfo(
            dailyEpochProgress = EpochProgress(NonNegLong(1)),
            epochProgressToReward = EpochProgress(NonNegLong(1)),
            amountToReward = toTokenAmountFormat(50),
            searchText = testQuery,
            dailyPostsNumber = 1,
            videos = List(VideoDetails("video3", "channel3", Instant.now(), 1000, 180))
          ))
        )
      )
    )

    val latticeUsers = List(
      LatticeUser("user1", Some(dagAddress1), Some(LinkedAccounts(Some(YouTubeAccount("channel1")), None)), None),
      LatticeUser("user2", Some(dagAddress2), Some(LinkedAccounts(Some(YouTubeAccount("channel2")), None)), None),
      LatticeUser("user3", Some(dagAddress3), Some(LinkedAccounts(Some(YouTubeAccount("channel3")), None)), None)
    )

    val result = YouTubeFetcher.filterLatticeUsers(
      latticeUsers,
      List(searchInfo),
      dataSource,
      None
    )

    result should have size 1
    result.head.primaryDagAddress shouldBe Some(dagAddress2)
  }

  test("should get all pending videos to search") {
    val dataSource = YouTubeDataSource(
      ListMap(
        dagAddress1 -> YouTubeDataSourceAddress(
          ListMap(testQuery -> YouTubeRewardInfo(
            dailyEpochProgress = EpochProgress(NonNegLong(1)),
            epochProgressToReward = EpochProgress(NonNegLong(1)),
            amountToReward = toTokenAmountFormat(50),
            searchText = testQuery,
            dailyPostsNumber = 1,
            videos = List.empty,
            rewardCandidates = List(VideoDetails("video1", "channel1", Instant.now(), 1000, 180)).some
          ))
        ),
        dagAddress2 -> YouTubeDataSourceAddress(
          ListMap(testQuery -> YouTubeRewardInfo(
            dailyEpochProgress = EpochProgress(NonNegLong(1)),
            epochProgressToReward = EpochProgress(NonNegLong(1)),
            amountToReward = toTokenAmountFormat(50),
            searchText = testQuery,
            dailyPostsNumber = 0,
            videos = List.empty,
            rewardCandidates = List(VideoDetails("video2", "channel2", Instant.now(), 1000, 180)).some
          ))
        )
      )
    )

    val mockSearchInfo = ApplicationConfig.YouTubeSearchInfo(
      text = testQuery,
      rewardAmount = Amount(NonNegLong(50L)),
      minimumDuration = 60.seconds,
      minimumViews = 50,
      maxPerDay = 1,
      publishedWithinHours = 3.hours,
      daysToMonitorVideoUpdates = 30.days
    )

    val result = YouTubeFetcher.getAllPendingVideosIds(
      dataSource,
      mockSearchInfo
    )

    result shouldBe List("video1", "video2")
  }

}
