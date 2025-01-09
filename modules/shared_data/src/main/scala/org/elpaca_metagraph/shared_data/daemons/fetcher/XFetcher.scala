package org.elpaca_metagraph.shared_data.daemons.fetcher

import cats.effect.Async
import cats.syntax.all._
import com.github.scribejava.apis.TwitterApi
import com.github.scribejava.core.builder.ServiceBuilder
import com.github.scribejava.core.model.{OAuth1AccessToken, OAuthRequest, Verb}
import com.github.scribejava.core.oauth.OAuth10aService
import fs2.io.net.Network
import org.elpaca_metagraph.shared_data.Utils.{isWithinDailyLimit, timeRangeFromDayStartTillNowFormatted}
import org.elpaca_metagraph.shared_data.app.ApplicationConfig
import org.elpaca_metagraph.shared_data.calculated_state.CalculatedStateService
import org.elpaca_metagraph.shared_data.types.DataUpdates.XUpdate
import org.elpaca_metagraph.shared_data.types.Lattice.LatticeUser
import org.elpaca_metagraph.shared_data.types.Refined.ApiUrl
import org.elpaca_metagraph.shared_data.types.States.{DataSourceType, XDataSource}
import org.elpaca_metagraph.shared_data.types.X._
import org.http4s._
import org.http4s.circe._
import org.http4s.client.Client
import org.tessellation.node.shared.resources.MkHttpClient
import org.tessellation.schema.address.Address
import org.typelevel.ci.CIString
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.time.{Instant, LocalDateTime, ZoneOffset}
import scala.jdk.CollectionConverters._

class XFetcher[F[_]: Async: Network](
  apiUrl            : ApiUrl,
  xApiConsumerKey   : String,
  xApiConsumerSecret: String,
  xApiAccessToken   : String,
  xApiAccessSecret  : String
)(implicit client: Client[F],
  logger: SelfAwareStructuredLogger[F]) {

  private val service: OAuth10aService = new ServiceBuilder(xApiConsumerKey)
    .apiSecret(xApiConsumerSecret)
    .build(TwitterApi.instance())

  def fetchXPosts(
    username       : String,
    searchText     : String,
    currentDateTime: LocalDateTime
  ): F[List[XPost]] = {
    val (startTime, endTime) = timeRangeFromDayStartTillNowFormatted(currentDateTime)
    val query = s"from:$username (\"$searchText\") -is:reply -is:retweet"
    val requestURI = Uri
      .unsafeFromString(apiUrl.toString())
      .withQueryParam("query", query)
      .withQueryParam("start_time", s"$startTime")
      .withQueryParam("end_time", s"$endTime")
      .withQueryParam("tweet.fields", "note_tweet")

    logger.info(s"Fetching X Url: ${requestURI.toString()}").flatMap { _ =>
      val oauthRequest = new OAuthRequest(Verb.GET, requestURI.toString())
      val token = new OAuth1AccessToken(xApiAccessToken, xApiAccessSecret)
      service.signRequest(token, oauthRequest)

      val headers = oauthRequest.getHeaders.entrySet().asScala.foldLeft(List.empty[Header.Raw]) { (acc, entry) =>
        acc :+ Header.Raw(CIString(entry.getKey), entry.getValue)
      }

      val signedRequest = Request[F](
        method = Method.GET,
        uri = Uri.unsafeFromString(oauthRequest.getCompleteUrl)
      ).withHeaders(headers)

      client.expect[XApiResponse](signedRequest)(jsonOf[F, XApiResponse]).map(_.data.getOrElse(List.empty[XPost]))
    }
  }
}

object XFetcher {
  private val groupsNumber: Int = 4
  private val xRateLimitMinutes: Int = 15

  def splitUsersIntoGroups(
    users: List[LatticeUser]
  ): List[List[LatticeUser]] = {
    val groupSize = (users.size / groupsNumber.toDouble).ceil.toInt
    users.grouped(groupSize).toList.take(groupsNumber)
  }

  def getCurrentGroupIndex: Int = (Instant.now().atZone(ZoneOffset.UTC).getMinute / xRateLimitMinutes) % groupsNumber

  def validateIfAddressCanProceed(
    xDataSource      : XDataSource,
    searchInformation: List[ApplicationConfig.XSearchInfo],
    address          : Address,
    maybePostId      : Option[String]
  ): Boolean = xDataSource.existingWallets.get(address).fold(true) { existingWallet =>
    val postIdNotUsed = maybePostId.forall { postId =>
      !searchInformation.exists { searchInfo =>
        existingWallet.addressRewards.get(searchInfo.text.toLowerCase).exists(_.postIds.contains(postId))
      }
    }

    postIdNotUsed && isWithinDailyLimit(searchInformation, existingWallet)
  }

  def filterXPosts(
    xPosts           : List[XDataInfo],
    currentPostsIds  : List[String],
    xDataSource      : XDataSource,
    searchInformation: List[ApplicationConfig.XSearchInfo]
  ): List[XDataInfo] = {
    val filteredPosts = xPosts.filter { xPost =>
      !currentPostsIds.contains(xPost.postId) && validateIfAddressCanProceed(
        xDataSource,
        searchInformation,
        xPost.dagAddress,
        xPost.postId.some
      )
    }

    removeAlreadyRewardedSearches(filteredPosts, searchInformation, xDataSource)
  }

  def removeAlreadyRewardedSearches(
  xPosts           : List[XDataInfo],
  searchInformation: List[ApplicationConfig.XSearchInfo],
  xDataSource      : XDataSource
  ): List[XDataInfo] = xPosts.filter { xPost =>
    xDataSource.existingWallets.get(xPost.dagAddress).fold(true) { existingWallet =>
      existingWallet.addressRewards.get(xPost.searchText.toLowerCase).fold(true) { xRewardInfo =>
        val searchTextMaxPerDay = searchInformation
          .find(_.text.toLowerCase === xRewardInfo.searchText.toLowerCase)
          .map(_.maxPerDay)
          .getOrElse(0L)

        xRewardInfo.dailyPostsNumber < searchTextMaxPerDay
      }
    }
  }

  def currentPostsIds(xDataSource: XDataSource): List[String] = xDataSource.existingWallets.values
    .flatMap(_.addressRewards.values)
    .flatMap(_.postIds)
    .toList

  def make[F[_]: Async: Network](
    applicationConfig     : ApplicationConfig,
    calculatedStateService: CalculatedStateService[F]
  ): Fetcher[F] = (currentDateTime: LocalDateTime) =>
    MkHttpClient.forAsync[F].newEmber(applicationConfig.http4s.client).use { implicit client =>
      implicit val logger: SelfAwareStructuredLogger[F] = Slf4jLogger.getLoggerFromClass(XFetcher.getClass)

      val xConfig = applicationConfig.xDaemon

      for {
        usersSourceApiUrl <- xConfig.usersSourceApiUrl.toOptionT.getOrRaise(new Exception(s"Could not get usersSourceApiUrl"))
        xApiUrl <- xConfig.xApiUrl.toOptionT.getOrRaise(new Exception(s"Could not get xApiUrl"))
        xApiConsumerKey <- xConfig.xApiConsumerKey.toOptionT.getOrRaise(new Exception(s"Could not get xApiConsumerKey"))
        xApiConsumerSecret <- xConfig.xApiConsumerSecret.toOptionT.getOrRaise(new Exception(s"Could not get xApiConsumerSecret"))
        xApiAccessToken <- xConfig.xApiAccessToken.toOptionT.getOrRaise(new Exception(s"Could not get xApiAccessToken"))
        xApiAccessSecret <- xConfig.xApiAccessSecret.toOptionT.getOrRaise(new Exception(s"Could not get xApiAccessSecret"))

        calculatedState <- calculatedStateService.get
        xDataSource: XDataSource = calculatedState.state.dataSources
          .get(DataSourceType.X)
          .collect { case ds: XDataSource => ds }
          .getOrElse(XDataSource(Map.empty))

        searchInformation = xConfig.searchInformation

        latticeFetcher = new LatticeFetcher[F](usersSourceApiUrl)
        xFetcher = new XFetcher[F](
          xApiUrl,
          xApiConsumerKey,
          xApiConsumerSecret,
          xApiAccessToken,
          xApiAccessSecret
        )
        sourceUsers <- latticeFetcher.fetchLatticeUsersWithXAccount()
        eligibleSourceUsers = sourceUsers.filter { user =>
          user.primaryDagAddress.flatMap { address =>
            user.linkedAccounts.twitter.map { _ =>
              validateIfAddressCanProceed(xDataSource, searchInformation, address, none)
            }
          }.getOrElse(false)
        }

        groups = splitUsersIntoGroups(eligibleSourceUsers)
        currentGroupIndex = getCurrentGroupIndex

        filteredUsers = groups(currentGroupIndex)
        _ <- logger.info(
          s"Found ${sourceUsers.length} sourceUsers" +
          s"\nFound ${filteredUsers.length} sourceUsersFiltered. Current group index: $currentGroupIndex"
        )

        searchInfo = searchInformation.map(_.text).mkString("\" OR \"")
        xPosts <- filteredUsers.traverse { userInfo =>
          val username = userInfo.linkedAccounts.twitter.get.username
          val primaryDAGAddress = userInfo.primaryDagAddress.get
          val xSearchResult = xFetcher.fetchXPosts(
            username,
            searchInfo,
            currentDateTime
          ).handleErrorWith { err =>
            logger.error(err)(s"Error when fetching XPosts for user: $username").as(List.empty[XPost])
          }

          xSearchResult.flatMap { _.filter { post =>
              searchInformation.exists(searchInfo => post.text.toLowerCase.contains(searchInfo.text.toLowerCase))
            }.traverse { xPost =>
              searchInformation
                .find(searchInfo => xPost.text.toLowerCase.contains(searchInfo.text.toLowerCase))
                .fold {
                  val defaultSearchInfo = searchInformation.head
                  logger.warn(
                    s"Could not get searchInformation from post: $xPost" +
                      s"\n\tSearch term: ${xPost.text}. Setting ${defaultSearchInfo.text}"
                  ).as(XDataInfo(
                    xPost.id,
                    primaryDAGAddress,
                    defaultSearchInfo.text,
                    defaultSearchInfo.maxPerDay
                  ))
                } { searchInfo =>
                  XDataInfo(
                    xPost.id,
                    primaryDAGAddress,
                    searchInfo.text,
                    searchInfo.maxPerDay
                  ).pure[F]
                }
            }
          }
        }.map(_.flatten)

        _ <- logger.info(s"Found ${xPosts.length} x posts")

        filteredXPosts = filterXPosts(xPosts, currentPostsIds(xDataSource), xDataSource, searchInformation)

        _ <- logger.info(s"Found ${filteredXPosts.length} valid x posts: $filteredXPosts")
        dataUpdates = filteredXPosts.foldLeft(List.empty[XUpdate]) { (acc, info) =>
          acc :+ XUpdate(info.dagAddress, info.searchText.toLowerCase, info.postId)
        }
      } yield dataUpdates
    }
}
