package org.elpaca_metagraph.shared_data.daemons.fetcher

import cats.effect.Async
import cats.syntax.all._
import fs2.io.net.Network
import org.elpaca_metagraph.shared_data.app.ApplicationConfig
import org.elpaca_metagraph.shared_data.app.ApplicationConfig.YouTubeSearchInfo
import org.elpaca_metagraph.shared_data.calculated_state.CalculatedStateService
import org.elpaca_metagraph.shared_data.types.DataUpdates.YouTubeUpdate
import org.elpaca_metagraph.shared_data.types.Refined.ApiUrl
import org.elpaca_metagraph.shared_data.types.States.{DataSourceType, YouTubeDataSource}
import org.elpaca_metagraph.shared_data.types.YouTube.LatticeClient.{LatticeUser, LatticeUsersApiResponse}
import org.elpaca_metagraph.shared_data.types.YouTube.YouTubeDataAPI.{SearchListResponse, VideoDetails, VideoListResponse}
import org.http4s.circe.jsonOf
import org.http4s.client.Client
import org.http4s.{Method, Request, Uri}
import org.tessellation.node.shared.resources.MkHttpClient
import org.tessellation.schema.address.Address
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.time.format.DateTimeFormatter
import java.time.{Duration, LocalDateTime, ZoneOffset}

class YouTubeFetcher[F[_] : Async : Network](apiKey: String, baseUrl: ApiUrl)(implicit client: Client[F]) {
  def fetchLatticeUsers(
    apiUrl: ApiUrl,
    offset: Long = 0,
    users: List[LatticeUser] = List.empty
  ): F[List[LatticeUser]] = {
    val request = Request[F](Method.GET, Uri.unsafeFromString(apiUrl.toString())
      .withQueryParam("limit", 100)
      .withQueryParam("offset", offset))

    client.expect[LatticeUsersApiResponse](request)(jsonOf[F, LatticeUsersApiResponse]).flatMap { response =>
      val newUsers = users ++ response.data

      if (!response.meta.exists(meta => meta.offset + meta.limit < meta.total)) {
        newUsers.filter(user => user.primaryDagAddress.isDefined && user.youtube.isDefined).pure
      } else fetchLatticeUsers(apiUrl, response.meta.get.offset + response.meta.get.limit, newUsers)
    }
  }

  def searchVideos(
    searchString: String,
    maxResults: Long = 50,
    publishedAfter: Option[LocalDateTime] = None,
    pageToken: Option[String] = None,
    result: Map[String, List[String]] = Map.empty
  ): F[Map[String, List[String]]] = {
    val formattedPublishAfter = publishedAfter.map(_.plusMinutes(1).atZone(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT))
    val request = Request[F](Method.GET, Uri.unsafeFromString(baseUrl.toString() + "/search")
      .withQueryParam("key", apiKey)
      .withQueryParam("q", searchString)
      .withQueryParam("type", "video")
      .withQueryParam("order", "date")
      .withQueryParam("maxResults", 50)
      .withQueryParam("part", "snippet")
      .withOptionQueryParam("publishedAfter", formattedPublishAfter)
      .withOptionQueryParam("pageToken", pageToken))

    client.expect[SearchListResponse](request)(jsonOf[F, SearchListResponse]).flatMap { response =>
      val currentMap = response.items.groupBy(_.snippet.channelId).view.mapValues(_.map(_.id.videoId)).toMap
      val updatedMap = result ++ currentMap.map {
        case (channelId, videoIds) => channelId -> (result.getOrElse(channelId, List.empty) ++ videoIds)
      }

      response.nextPageToken match {
        case Some(token) => searchVideos(
          searchString,
          maxResults,
          publishedAfter,
          Some(token),
          updatedMap
        )
        case None => updatedMap.pure[F]
      }
    }
  }

  def filterVideosByChannels(
    channelVideoMap: Map[String, List[String]],
    allowedChannelIds: List[String]
  ): List[String] =
    channelVideoMap.view
      .filterKeys(allowedChannelIds.contains)
      .values
      .flatten
      .toList

  def fetchVideoUpdates(
    users: List[LatticeUser],
    searchInfo: YouTubeSearchInfo,
    globalSearchResult: Map[String, List[String]]
  ): F[List[YouTubeUpdate]] =
    users.traverse { user =>
      val videoIds = filterVideosByChannels(globalSearchResult, List(user.youtube.get.channelId))
      fetchVideoDetails(
        videoIds,
        searchInfo.minimumDuration,
        searchInfo.minimumViews
      ).map(_.map { video => YouTubeUpdate(
        user.primaryDagAddress.get,
        searchInfo.text,
        video
      )})
    }.map(_.flatten)

  private def fetchVideoDetails(
    videosIds: List[String],
    minimumDuration: Long,
    minimumViews: Long
  ): F[List[VideoDetails]] =
    if (videosIds.isEmpty) Async[F].pure(Nil)
    else {
      val request = Request[F](Method.GET, Uri.unsafeFromString(baseUrl.toString() + "/videos")
        .withQueryParam("key", apiKey)
        .withQueryParam("id", videosIds.mkString(","))
        .withQueryParam("part", "snippet,contentDetails,statistics"))

      client.expect[VideoListResponse](request)(jsonOf[F, VideoListResponse]).flatMap { response =>
        response.items.map(item =>
          VideoDetails(
            id = item.id,
            channelId = item.snippet.channelId,
            publishedAt = item.snippet.publishedAt,
            views = item.statistics.viewCount.getOrElse(0),
            duration = Duration.parse(item.contentDetails.duration).getSeconds
          )
        ).filter(videoDetails => videoDetails.duration > minimumDuration && videoDetails.views > minimumViews).pure
      }
    }
}

object YouTubeFetcher {
  private val zoneOffset = ZoneOffset.UTC

  def make[F[_] : Async : Network](
    applicationConfig: ApplicationConfig,
    calculatedStateService: CalculatedStateService[F]
  ): Fetcher[F] = (_: LocalDateTime) =>
    MkHttpClient.forAsync[F].newEmber(applicationConfig.http4s.client).use { implicit client =>
      val logger: SelfAwareStructuredLogger[F] = Slf4jLogger.getLoggerFromClass(YouTubeFetcher.getClass)
      val config = applicationConfig.youtubeDaemon

      for {
        latticeApiUrl <- config.usersSourceApiUrl.toOptionT.getOrRaise(new Exception(s"Could not get usersSourceApiUrl"))
        baseUrl <- config.youtubeApiUrl.toOptionT.getOrRaise(new Exception(s"Could not get YouTube Data API baseUrl"))
        apiKey <- config.youtubeApiKey.toOptionT.getOrRaise(new Exception(s"Could not get YouTube apiKey"))
        searchInformation = config.searchInformation

        youtubeFetcher = new YouTubeFetcher[F](apiKey, baseUrl)
        calculatedState <- calculatedStateService.get
        dataSource: YouTubeDataSource = calculatedState.state.dataSources
          .get(DataSourceType.YouTube)
          .collect { case ds: YouTubeDataSource => ds }
          .getOrElse(YouTubeDataSource(Map.empty))

        latticeUsers <- youtubeFetcher.fetchLatticeUsers(latticeApiUrl)
        filteredLatticeUsers = latticeUsers.filter { user =>
          user.primaryDagAddress.flatMap { address =>
            user.youtube.map { _ =>
              validateIfAddressCanProceed(dataSource, searchInformation, address, none)
            }
          }.getOrElse(false)
        }
        newlyLoadedLatticeUsers = latticeUsers.filterNot { user =>
          dataSource.existingWallets.keys.toList.contains(user.primaryDagAddress.get)
        }

        _ <- logger.info(s"Found ${latticeUsers.length} Lattice users")
        _ <- logger.info(s"Found ${filteredLatticeUsers.length} filtered Lattice users")
        _ <- logger.info(s"Found ${newlyLoadedLatticeUsers.length} newly loaded Lattice users")

        updates <- searchInformation.traverse { searchInfo =>
          for {
            globalSearchResult <- youtubeFetcher.searchVideos(
              s"constellation-${searchInfo.text}",
              searchInfo.maxPerDay,
              Some(LocalDateTime.now().minusHours(searchInfo.publishedWithinHours))
            )
            dataUpdates <- youtubeFetcher.fetchVideoUpdates(filteredLatticeUsers, searchInfo, globalSearchResult)
            newUpdates <- youtubeFetcher.fetchVideoUpdates(newlyLoadedLatticeUsers, searchInfo, globalSearchResult)
          } yield dataUpdates ++ newUpdates
        }.map(_.flatten)

        filteredUpdates = updates.filter { update =>
          validateIfAddressCanProceed(
            dataSource,
            searchInformation,
            update.address,
            Some(update.video)
          )
        }
      } yield filteredUpdates
    }

  private def validateIfAddressCanProceed(
    dataSource: YouTubeDataSource,
    searchInformation: List[ApplicationConfig.YouTubeSearchInfo],
    address: Address,
    maybeVideo: Option[VideoDetails]
  ): Boolean =
    dataSource.existingWallets.get(address).fold(true) { existingWallet =>
      searchInformation.exists { searchInfo =>
        existingWallet.addressRewards.get(searchInfo.text).fold(true) { addressRewards =>
          addressRewards.dailyPostsNumber < searchInfo.maxPerDay &&
            maybeVideo.forall(!addressRewards.videos.contains(_))
        }
      }
    }
}
