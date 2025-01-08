package org.elpaca_metagraph.shared_data.daemons.fetcher

import cats.effect._
import cats.effect.unsafe.implicits.global
import eu.timepit.refined.auto._
import eu.timepit.refined.types.numeric.NonNegLong
import io.circe.syntax._
import org.elpaca_metagraph.shared_data.Utils
import org.elpaca_metagraph.shared_data.Utils.timeRangeFromDayStartTillNowFormatted
import org.elpaca_metagraph.shared_data.app.ApplicationConfig
import org.elpaca_metagraph.shared_data.types.Lattice.{LatticeUser, XAccount}
import org.elpaca_metagraph.shared_data.types.States.XDataSource
import org.elpaca_metagraph.shared_data.types.X._
import org.http4s._
import org.http4s.circe._
import org.http4s.client.Client
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.tessellation.schema.address.Address
import org.tessellation.schema.epoch.EpochProgress

import java.time.LocalDateTime
import scala.collection.immutable.ListMap

class XFetcherSuite extends AnyFunSuite with Matchers with FetcherSuite {

  private val DAG = "#$dag"
  private val AMERICASBLOCKCHAIN = "#americasblockchain"
  private val searchInfo = List(
    ApplicationConfig.XSearchInfo(DAG, Utils.toTokenAmountFormat(5), maxPerDay = 1),
    ApplicationConfig.XSearchInfo(AMERICASBLOCKCHAIN, Utils.toTokenAmountFormat(5), maxPerDay = 1)
  )
  private val post1 = XPost("post1", s"Some note about $DAG", None)
  private val post2 = XPost("post2", s"Some other note about ${DAG.toUpperCase()}", None)
  private val post3 = XPost("post3", s"Some note about $AMERICASBLOCKCHAIN", None)
  private val post4 = XPost("post4", s"Some other note about ${AMERICASBLOCKCHAIN.toUpperCase()}", None)
  private val post5 = XPost("post5", s"A post with both $DAG and $AMERICASBLOCKCHAIN tags", None)
  private val post6 = XPost("post6", s"Another post with both $AMERICASBLOCKCHAIN and $DAG tags", None)
  private val post7 = XPost("post7", "A random post without any search term in it", None)
  private val post8 = XPost("post8", "Another random post without any search term in it", None)
  private val posts = List(post1, post2, post3, post4, post5, post6, post7, post8)
  private val user1 = createUser(1, dagAddress1)
  private val user2 = createUser(2, dagAddress2)
  private val user3 = createUser(3, Address("DAG7B91BBsVAyoKsWkwK4AvALHuzYSyxxFPSJ2jY"))
  private val user4 = createUser(3, Address("DAG6aewPmSWoyRNZx4QgyTBbQxwsGRR1SJN1peQS"))
  private val users = List(user1, user2, user3, user4)
  private val epochProgress = EpochProgress(NonNegLong(1))
  private val xRewardInfo1 = XRewardInfo(epochProgress, epochProgress, Utils.toTokenAmountFormat(1), DAG, List(post1.id), 1)
  private val xRewardInfo2 = XRewardInfo(epochProgress, epochProgress, Utils.toTokenAmountFormat(1), DAG, List(post5.id), 1)
  private val xPostsResponse = XApiResponse(
    data = Some(posts),
    meta = XApiResponseMetadata(4)
  )
  private val xDagPostsResponse = XApiResponse(
    data = Some(posts.filter(_.text.toLowerCase().contains(DAG))),
    meta = XApiResponseMetadata(4)
  )
  private val xABPostsResponse = XApiResponse(
    data = Some(posts.filter(_.text.toLowerCase().contains(AMERICASBLOCKCHAIN))),
    meta = XApiResponseMetadata(4)
  )

  private def createUser(i: Int, dagAddress: Address): LatticeUser =
    LatticeUser(s"user$i", Some(dagAddress), None, Some(XAccount(s"account$i")))

  private def buildUriForUser(
    username: String,
    searchText: String,
    currentDate: LocalDateTime,
    expectedResponse: XApiResponse = xPostsResponse
  ): Map[Uri, Response[IO]] = {
    val (startTime, endTime) = timeRangeFromDayStartTillNowFormatted(currentDate)

    Map(Uri
      .unsafeFromString(baseUrl.toString())
      .withQueryParam("query", s"from:$username (\"$searchText\") -is:reply -is:retweet")
      .withQueryParam("start_time", s"$startTime")
      .withQueryParam("end_time", s"$endTime")
      .withQueryParam("tweet.fields", "note_tweet")
      -> Response[IO](Status.Ok).withEntity(expectedResponse.asJson))
  }

  test("fetchXPosts should fetch and filter posts with #$dag correctly") {
    val searchText = DAG
    val currentDate = LocalDateTime.now()

    implicit val client: Client[IO] = mockClient(buildUriForUser(user1.id, searchText, currentDate, xDagPostsResponse))

    val fetcher = new XFetcher[IO](
      apiUrl = baseUrl,
      xApiConsumerKey = "key",
      xApiConsumerSecret = "secret",
      xApiAccessToken = "token",
      xApiAccessSecret = "secret"
    )

    val posts = fetcher.fetchXPosts(user1.id, searchText, currentDate).unsafeRunSync()
    posts should have size 4
    posts.map(_.id) should contain theSameElementsAs List(post1.id, post2.id, post5.id, post6.id)
  }

  test("fetchXPosts should fetch and filter posts with #americasblockchain correctly") {
    val searchText = AMERICASBLOCKCHAIN
    val currentDate = LocalDateTime.now()

    implicit val client: Client[IO] = mockClient(buildUriForUser(user1.id, searchText, currentDate, xABPostsResponse))

    val fetcher = new XFetcher[IO](
      apiUrl = baseUrl,
      xApiConsumerKey = "key",
      xApiConsumerSecret = "secret",
      xApiAccessToken = "token",
      xApiAccessSecret = "secret"
    )

    val posts = fetcher.fetchXPosts(user1.id, searchText, currentDate).unsafeRunSync()
    posts should have size 4
    posts.map(_.id) should contain theSameElementsAs List(post3.id, post4.id, post5.id, post6.id)
  }

  test("splitUsersIntoGroups should divide users correctly") {
    val result = XFetcher.splitUsersIntoGroups(users)
    result should have size 4
    result.flatten should have size 4
  }

  test("filterXPosts should filter out posts with same tag regardless if it's in uppercase or lowercase") {
    val xPosts = List(
      XDataInfo(post1.id, dagAddress1, DAG, 1), // lowercase, already rewarded
      XDataInfo(post2.id, dagAddress1, DAG, 1)  // uppercase
    )

    val mockDataSource = XDataSource(Map(
      dagAddress1 -> XDataSourceAddress(ListMap(DAG -> xRewardInfo1))
    ))

    val result = XFetcher.filterXPosts(xPosts, XFetcher.currentPostsIds(mockDataSource), mockDataSource, searchInfo)
    result should have size 0 // post1 and post2 have the same tag and post1 has already been rewarded
  }

  test("validateIfAddressCanProceed should verify if post has already been rewarded") {
    val mockDataSource = XDataSource(Map(
      dagAddress1 -> XDataSourceAddress(ListMap(DAG -> xRewardInfo1))
    ))

    val case1 = XFetcher.validateIfAddressCanProceed(mockDataSource, searchInfo, dagAddress1, Some(post1.id))
    val case2 = XFetcher.validateIfAddressCanProceed(mockDataSource, searchInfo, dagAddress1, Some(post2.id))

    case1 shouldBe false
    case2 shouldBe true
  }

  test("removeAlreadyRewardedSearches should filter out posts with tags that have already reached daily limit") {
    val xPosts = List(
      XDataInfo(post3.id, dagAddress1, AMERICASBLOCKCHAIN, 1),
      XDataInfo(post5.id, dagAddress1, DAG, 1)
    )

    val mockDataSource = XDataSource(Map(
      dagAddress1 -> XDataSourceAddress(ListMap(DAG -> xRewardInfo2))
    ))

    val result = XFetcher.removeAlreadyRewardedSearches(xPosts, searchInfo, mockDataSource)

    result should have size 1
    result.map(_.postId) should contain theSameElementsAs List(post3.id)
  }
}
