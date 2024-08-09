package org.elpaca_metagraph.shared_data.daemons.fetcher


import cats.effect.{Async, Resource}
import cats.syntax.all._
import com.github.scribejava.apis.TwitterApi
import com.github.scribejava.core.builder.ServiceBuilder
import com.github.scribejava.core.model.{OAuth1AccessToken, OAuthRequest, Verb}
import com.github.scribejava.core.oauth.OAuth10aService
import fs2.io.net.Network
import io.circe.generic.auto._
import org.elpaca_metagraph.shared_data.app.ApplicationConfig
import org.elpaca_metagraph.shared_data.calculated_state.CalculatedStateService
import org.elpaca_metagraph.shared_data.types.DataUpdates.{ElpacaUpdate, XUpdate}
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

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import scala.jdk.CollectionConverters._

object XFetcher {

  def make[F[_] : Async : Network](
    applicationConfig     : ApplicationConfig,
    calculatedStateService: CalculatedStateService[F]
  ): Fetcher[F] =
    new Fetcher[F] {
      private val logger: SelfAwareStructuredLogger[F] = Slf4jLogger.getLoggerFromClass(XFetcher.getClass)

      def fetchAllSourceUsers(
        baseUrl         : ApiUrl,
        initialOffset   : Long = 0,
        accumulatedUsers: List[SourceUser] = List.empty
      ): F[List[SourceUser]] = {
        val clientResource: Resource[F, Client[F]] = MkHttpClient.forAsync[F].newEmber(applicationConfig.http4s.client)
        val usersPerRequest: Long = 100
        clientResource.use { client =>
          val requestURI = Uri.unsafeFromString(baseUrl.toString())
            .withQueryParam("limit", usersPerRequest)
            .withQueryParam("offset", initialOffset)

          val request = Request[F](
            method = Method.GET,
            uri = requestURI
          )

          client.expect[SourceUsersApiResponse](request)(jsonOf[F, SourceUsersApiResponse]).flatMap { response =>
            val newUsers = accumulatedUsers ++ response.data
            if (response.meta.offset + response.meta.limit < response.meta.total) {
              fetchAllSourceUsers(baseUrl, response.meta.offset + response.meta.limit, newUsers)
            } else {
              newUsers.pure
            }
          }
        }
      }

      def fetchXPosts(
        username          : String,
        searchText        : String,
        url               : ApiUrl,
        xApiConsumerKey   : String,
        xApiConsumerSecret: String,
        xApiAccessToken   : String,
        xApiAccessSecret  : String,
        currentDateTime   : LocalDateTime
      ):
      F[List[XPost]] = {
        val clientResource: Resource[F, Client[F]] = MkHttpClient.forAsync[F].newEmber(applicationConfig.http4s.client)

        val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
        val currentDateFormatted: String = currentDateTime.format(dateFormatter)
        val currentDateTimeFormatted: String = currentDateTime.minusMinutes(1).format(dateTimeFormatter)

        val service: OAuth10aService = new ServiceBuilder(xApiConsumerKey)
          .apiSecret(xApiConsumerSecret)
          .build(TwitterApi.instance())

        clientResource.use { client =>
          val query = s"from:$username \"$searchText\""
          val requestURI = Uri.unsafeFromString(url.toString()).withQueryParam("query", query)
            .withQueryParam("start_time", s"${currentDateFormatted}T00:00:00Z")
            .withQueryParam("end_time", s"$currentDateTimeFormatted")
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

      override def getAddressesAndBuildUpdates(currentDateTime: LocalDateTime): F[List[ElpacaUpdate]] = {
        val xConfig = applicationConfig.xDaemon

        def validateIfAddressCanProceed(
          xDataSource      : XDataSource,
          searchInformation: List[ApplicationConfig.XSearchInfo],
          address          : Address,
          maybePostId      : Option[String]
        ): Boolean =
          xDataSource.existingWallets.get(address).fold(true) { existingWallet =>
            searchInformation.exists { searchInfo =>
              existingWallet.addressRewards.get(searchInfo.text).fold(true) { addressRewards =>
                addressRewards.dailyPostsNumber < searchInfo.maxPerDay &&
                  maybePostId.forall(!addressRewards.postIds.contains(_))
              }
            }
          }

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

          sourceUsers <- fetchAllSourceUsers(usersSourceApiUrl)
          sourceUsersFiltered = sourceUsers.filter { user =>
            user.primaryDagAddress.flatMap { address =>
              user.twitter.map { _ =>
                validateIfAddressCanProceed(xDataSource, searchInformation, address, none)
              }
            }.getOrElse(false)
          }

          _ <- logger.info(s"Found ${sourceUsers.length} sourceUsers")
          _ <- logger.info(s"Found ${sourceUsersFiltered.length} sourceUsersFiltered")

          xPosts <- sourceUsersFiltered.traverse { userInfo =>
            val username = userInfo.twitter.get.username
            val primaryDAGAddress = userInfo.primaryDagAddress.get
            searchInformation.traverse { searchInfo =>
              fetchXPosts(
                username,
                searchInfo.text,
                xApiUrl,
                xApiConsumerKey,
                xApiConsumerSecret,
                xApiAccessToken,
                xApiAccessSecret,
                currentDateTime
              )
                .handleErrorWith { err =>
                  logger.error(err)(s"Error when fetching XPosts for user: $username").as(List.empty[XPost])
                }
                .map { xPosts =>
                  xPosts.map { xPost =>
                    XDataInfo(
                      xPost.id,
                      primaryDAGAddress,
                      searchInfo.text,
                      searchInfo.maxPerDay
                    )
                  }
                }
            }.map(_.flatten)
          }.map(_.flatten)

          _ <- logger.info(s"Found ${xPosts.length} x posts")

          filteredXPosts = xPosts.filter { xPost =>
            validateIfAddressCanProceed(xDataSource, searchInformation, xPost.dagAddress, xPost.postId.some)
          }
          _ <- logger.info(s"Found ${filteredXPosts.length} valid x posts: ${filteredXPosts}")
          dataUpdates = filteredXPosts.foldLeft(List.empty[XUpdate]) { (acc, info) =>
            acc :+ XUpdate(info.dagAddress, info.searchText, info.postId)
          }
        } yield dataUpdates
      }
    }
}
