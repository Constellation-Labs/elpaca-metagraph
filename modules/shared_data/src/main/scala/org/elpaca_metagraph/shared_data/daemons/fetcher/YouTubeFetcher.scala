package org.elpaca_metagraph.shared_data.daemons.fetcher

import cats.effect.Async
import cats.syntax.all._
import fs2.io.net.Network
import org.elpaca_metagraph.shared_data.Utils.isWithinDailyLimit
import org.elpaca_metagraph.shared_data.app.ApplicationConfig
import org.elpaca_metagraph.shared_data.app.ApplicationConfig.{SearchInfo, YouTubeSearchInfo}
import org.elpaca_metagraph.shared_data.calculated_state.CalculatedStateService
import org.elpaca_metagraph.shared_data.daemons.fetcher.YouTubeFetcher.logInfo
import org.elpaca_metagraph.shared_data.types.DataUpdates.YouTubeUpdate
import org.elpaca_metagraph.shared_data.types.Lattice.LatticeUser
import org.elpaca_metagraph.shared_data.types.Refined.ApiUrl
import org.elpaca_metagraph.shared_data.types.States.{DataSourceType, YouTubeDataSource}
import org.elpaca_metagraph.shared_data.types.YouTube.YouTubeDataAPI._
import org.http4s._
import org.http4s.circe.jsonOf
import org.http4s.client.Client
import org.tessellation.node.shared.resources.MkHttpClient
import org.tessellation.schema.address.Address
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.format.DateTimeFormatter
import java.time.{Duration, LocalDateTime, ZoneOffset}

class YouTubeFetcher[F[_]: Async: Network](
  apiKey : String,
  baseUrl: ApiUrl
)(implicit client: Client[F],
  logger: SelfAwareStructuredLogger[F]) {

  def searchVideos(
    searchString  : String,
    maxResults    : Long = 50,
    publishedAfter: Option[LocalDateTime] = None,
    pageToken     : Option[String] = None,
    result        : Map[String, List[String]] = Map.empty
  ): F[Map[String, List[String]]] = {
    val formattedPublishAfter = publishedAfter.map(_.plusMinutes(1).atZone(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT))
    val request = Request[F](
      Method.GET,
      Uri
        .unsafeFromString(s"$baseUrl/search")
        .withQueryParam("key", apiKey)
        .withQueryParam("q", URLEncoder.encode(searchString, StandardCharsets.UTF_8))
        .withQueryParam("type", "video")
        .withQueryParam("order", "date")
        .withQueryParam("maxResults", 50)
        .withQueryParam("part", "snippet")
        .withOptionQueryParam("publishedAfter", formattedPublishAfter)
        .withOptionQueryParam("pageToken", pageToken)
    )

    for {
      _ <- logInfo(s"Searching for videos with search string $searchString")
      response <- client.expect[SearchListResponse](request)(jsonOf[F, SearchListResponse]).handleErrorWith { e =>
        logger
          .error(e)(s"Error searching for videos with search string $searchString: ${e.getMessage}")
          .as(SearchListResponse(List.empty, None, PageInfo(0)))
      }
      currentMap = response.items.groupBy(_.snippet.channelId).view.mapValues(_.map(_.id.videoId)).toMap
      updatedMap = result ++ currentMap.map {
        case (channelId, videoIds) => channelId -> (result.getOrElse(channelId, List.empty) ++ videoIds)
      }
      _ <- logInfo(
        s"Total results to be parsed for search term" +
          s" {$searchString}: ${response.pageInfo.totalResults - updatedMap.values.flatten.size} from the total of" +
          s" ${response.pageInfo.totalResults}"
      )
      result <- response.nextPageToken match {
        case Some(token) =>
          searchVideos(
            searchString,
            maxResults,
            publishedAfter,
            Some(token),
            updatedMap
          )
        case None => updatedMap.pure[F]
      }
      _ <- logInfo(s"Found ${result.values.flatten.size} videos for search string $searchString")
    } yield result
  }

  def filterVideosByChannels(
    channelVideoMap  : Map[String, List[String]],
    allowedChannelIds: List[String]
  ): List[String] =
    channelVideoMap.view
      .filterKeys(allowedChannelIds.toSet)
      .values
      .flatten
      .toList

  def fetchVideoUpdates(
    users             : List[LatticeUser],
    searchInfo        : YouTubeSearchInfo,
    globalSearchResult: Map[String, List[String]]
  ): F[List[YouTubeUpdate]] = {
    val channelIds = users.flatMap(_.linkedAccounts.youtube.map(_.channelId))
    val videoIds = filterVideosByChannels(globalSearchResult, channelIds)

    fetchVideoDetails(
      videoIds,
      searchInfo.minimumDuration,
      searchInfo.minimumViews
    ).flatMap { videos =>
      users.traverse { user =>
        videos.filter(_.channelId == user.linkedAccounts.youtube.get.channelId).map { video =>
          YouTubeUpdate(user.primaryDagAddress.get, searchInfo.text.toLowerCase, video)
        }.pure
      }
    }.map(_.flatten)
  }

  def fetchVideoDetails(
    videosIds      : List[String],
    minimumDuration: Long,
    minimumViews   : Long
  ): F[List[VideoDetails]] = {
    if (videosIds.isEmpty) {
      logInfo("No videos to fetch").as(Nil)
      Async[F].pure(Nil)
    }
    else {
      val request = Request[F](
        Method.GET,
        Uri
          .unsafeFromString(s"$baseUrl/videos")
          .withQueryParam("key", apiKey)
          .withQueryParam("id", videosIds.mkString(","))
          .withQueryParam("part", "snippet,contentDetails,statistics")
      )

      client
        .expect[VideoListResponse](request)(jsonOf[F, VideoListResponse])
        .flatMap { response =>
          response.items.map { item =>
            val views: Long = item.statistics.viewCount.getOrElse(0)
            val duration: Long = Duration.parse(item.contentDetails.duration).getSeconds

            if (views < minimumViews || duration < minimumDuration) {
              logger.warn(s"Video id ${item.id} with duration $duration and views $views does not meet the criteria")
            }

            VideoDetails(
              id = item.id,
              channelId = item.snippet.channelId,
              publishedAt = item.snippet.publishedAt,
              views = item.statistics.viewCount.getOrElse(0),
              duration = duration
            )
          }.filter(videoDetails => videoDetails.duration >= minimumDuration && videoDetails.views >= minimumViews).pure
        }
        .handleErrorWith { e =>
          logger.error(e)(s"Error fetching video details: ${e.getMessage}").as(List.empty[VideoDetails])
        }
    }
  }
}

object YouTubeFetcher {
  def make[F[_]: Async: Network](
    applicationConfig     : ApplicationConfig,
    calculatedStateService: CalculatedStateService[F]
  ): Fetcher[F] = (_: LocalDateTime) =>
    MkHttpClient.forAsync[F].newEmber(applicationConfig.http4s.client).use { implicit client =>
      implicit val logger: SelfAwareStructuredLogger[F] = Slf4jLogger.getLoggerFromClass(YouTubeFetcher.getClass)
      val config = applicationConfig.youtubeDaemon

      for {
        latticeApiUrl <- config.usersSourceApiUrl.toOptionT.getOrRaise(new Exception(s"Could not get usersSourceApiUrl"))
        baseUrl <- config.youtubeApiUrl.toOptionT.getOrRaise(new Exception(s"Could not get YouTube Data API baseUrl"))
        apiKey <- config.youtubeApiKey.toOptionT.getOrRaise(new Exception(s"Could not get YouTube apiKey"))
        searchInformation = config.searchInformation

        latticeUsersFetcher = new LatticeFetcher[F](latticeApiUrl)
        youtubeFetcher = new YouTubeFetcher[F](apiKey, baseUrl)
        calculatedState <- calculatedStateService.get
        dataSource: YouTubeDataSource = calculatedState.state.dataSources
          .get(DataSourceType.YouTube)
          .collect { case ds: YouTubeDataSource => ds }
          .getOrElse(YouTubeDataSource(Map.empty))

        latticeUsers <- latticeUsersFetcher.fetchLatticeUsersWithYouTubeAccount()

        filteredLatticeUsers = latticeUsers.filter { user =>
          user.primaryDagAddress.flatMap { address =>
            user.linkedAccounts.youtube.map { _ =>
              validateIfAddressCanProceed(dataSource, searchInformation, address, none)
            }
          }.getOrElse(false)
        }

        _ <- logInfo(
          s"YouTube Lattice fetcher started. " +
          s"\n\tTotal Lattice users: ${latticeUsers.length}, " +
          s"\n\tFiltered: ${filteredLatticeUsers.length}"
        )

        updates <- searchInformation.traverse { searchInfo =>
          for {
            globalSearchResult <- youtubeFetcher.searchVideos(
              s"constellation-${searchInfo.text}",
              searchInfo.maxPerDay,
              Some(LocalDateTime.now().minusHours(searchInfo.publishedWithinHours))
            )

            dataUpdates <- youtubeFetcher.fetchVideoUpdates(filteredLatticeUsers, searchInfo, globalSearchResult)
          } yield dataUpdates
        }.map(_.flatten)

        filteredUpdates = updates.filter { update =>
          validateIfAddressCanProceed(dataSource, searchInformation, update.address, Some(update.video))
        }
      } yield filteredUpdates
    }

  def logInfo[F[_]: Async: Network](
    message: String
  )(implicit logger: SelfAwareStructuredLogger[F]
  ): F[Unit] =
    logger.info(s"[YouTubeFetcher] $message").flatMap(_ => Async[F].unit)

  private def validateIfAddressCanProceed(
    dataSource       : YouTubeDataSource,
    searchInformation: List[SearchInfo],
    address          : Address,
    maybeVideo       : Option[VideoDetails]
  ): Boolean = dataSource.existingWallets.get(address).fold(true) { wallet =>
    val videoNotRewarded = maybeVideo.fold(true) { video =>
      !searchInformation.exists { searchInfo =>
        wallet.addressRewards.get(searchInfo.text.toLowerCase).exists(_.videos.contains(video))
      }
    }

    videoNotRewarded && isWithinDailyLimit(searchInformation, wallet)
  }
}
