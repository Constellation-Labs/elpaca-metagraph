package org.elpaca_metagraph.shared_data.daemons.fetcher

import cats.effect._
import cats.effect.testkit.TestControl
import eu.timepit.refined.auto._
import eu.timepit.refined.types.numeric.NonNegLong
import io.circe.syntax._
import org.elpaca_metagraph.shared_data.Utils
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

  private val searchInfo = List(ApplicationConfig.XSearchInfo("test", Utils.toTokenAmountFormat(5), maxPerDay = 1))
  private val post1 = XPost("post1", "dag", Some(NoteTweet("Some note about dag")))
  private val post2 = XPost("post2", "dag", Some(NoteTweet("Some other note about dag")))
  private val post3 = XPost("post3", "americasblockchain", Some(NoteTweet("Some note about americasblockchain")))
  private val post4 = XPost("post4", "americasblockchain", Some(NoteTweet("Some other note about americasblockchain")))
  private val user1 = createUser(1, dagAddress1)
  private val user2 = createUser(2, dagAddress2)
  private val user3 = createUser(3, Address("DAG7B91BBsVAyoKsWkwK4AvALHuzYSyxxFPSJ2jY"))
  private val user4 = createUser(3, Address("DAG6aewPmSWoyRNZx4QgyTBbQxwsGRR1SJN1peQS"))
  private val users = List(user1, user2, user3, user4)
  private val epochProgress = EpochProgress(NonNegLong(1))
  private val xRewardInfo1 = XRewardInfo(epochProgress, epochProgress, Utils.toTokenAmountFormat(1), "dag", List("post1"), 1)
  private val xPostsResponse = XApiResponse(
    data = Some(List(post1, post2, post3, post4)),
    meta = XApiResponseMetadata(4)
  )

  private def createUser(i: Int, dagAddress: Address): LatticeUser =
    LatticeUser(s"user$i", Some(dagAddress), None, Some(XAccount(s"account$i")))

  test("fetchXPosts should fetch and filter posts correctly") {
    implicit val client: Client[IO] = mockClient(Map(
      Uri.unsafeFromString(baseUrl.toString()) -> Response[IO](Status.Ok).withEntity(xPostsResponse.asJson)
    ))

    val fetcher = new XFetcher[IO](
      apiUrl = baseUrl,
      xApiConsumerKey = "key",
      xApiConsumerSecret = "secret",
      xApiAccessToken = "token",
      xApiAccessSecret = "secret"
    )

    val result = TestControl.execute {
      for {
        posts <- fetcher.fetchXPosts(
          username = "user1",
          searchText = "dag",
          currentDateTime = LocalDateTime.now()
        )
        _ <- IO {
          posts should have size 2
          posts.map(_.id) should contain theSameElementsAs List("post1", "post2")
        }
      } yield ()
    }

    result.flatMap(_.tickAll)
  }

  test("splitUsersIntoGroups should divide users correctly") {
    val result = XFetcher.splitUsersIntoGroups(users)
    result should have size 4
    result.flatten should have size 4
  }

  test("validateIfAddressCanProceed should respect daily limits") {
    val mockDataSource = XDataSource(Map(
      dagAddress1 -> XDataSourceAddress(ListMap("dag" -> xRewardInfo1))
    ))

    val result = XFetcher.validateIfAddressCanProceed(mockDataSource, searchInfo, dagAddress1, Some("post1"))

    result shouldBe true
  }

  test("filterAlreadyRewardedSearches should filter out rewarded posts") {
    val xPosts = List(
      XDataInfo("post1", dagAddress1, "dag", 1),
      XDataInfo("post2", dagAddress2, "dag", 1)
    )

    val mockDataSource = XDataSource(Map(
      dagAddress1 -> XDataSourceAddress(ListMap("dag" -> xRewardInfo1))
    ))

    val result = XFetcher.filterAlreadyRewardedSearches(xPosts, searchInfo, mockDataSource)

    result should have size 1
    result.head.postId shouldBe "post2"
  }
}
