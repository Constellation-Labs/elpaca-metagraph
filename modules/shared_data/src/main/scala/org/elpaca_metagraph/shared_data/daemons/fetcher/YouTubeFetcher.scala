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

class YouTubeFetcher[F[_] : Async : Network](
  apiKey : String,
  baseUrl: ApiUrl
)(implicit client: Client[F],
  logger         : SelfAwareStructuredLogger[F]) {

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
        s"Total results parsed for search term" +
          s" {$searchString}: ${updatedMap.values.flatten.size} of" +
          s" ${response.pageInfo.totalResults}"
      )
      finalResult <- response.nextPageToken match {
        case Some(token) =>
          searchVideos(
            searchString,
            maxResults,
            publishedAfter,
            Some(token),
            updatedMap
          )
        case None =>
          val (totalChannels, totalResults) = (updatedMap.size, updatedMap.values.flatten.size)
          logInfo(s"Found $totalResults video${if (totalResults > 1) "s" else ""} for " +
            s"$totalChannels channel${if (totalChannels > 1) "s" else ""} with search term {$searchString}").as(updatedMap)
      }
    } yield finalResult
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
    users     : List[LatticeUser],
    videoIds  : List[String],
    searchInfo: YouTubeSearchInfo
  ): F[List[YouTubeUpdate]] = {
    fetchVideoDetails(
      videoIds,
      searchInfo.minimumDuration.toSeconds
    ).flatMap { videos =>
      val filteredVideos = videos
        .filter(_.duration >= searchInfo.minimumDuration.toSeconds)
      users.traverse { user =>
        filteredVideos.filter(_.channelId == user.linkedAccounts.get.youtube.get.channelId).map { video =>
          YouTubeUpdate(user.primaryDagAddress.get, searchInfo.text.toLowerCase, video)
        }.pure
      }
    }.map(_.flatten)
  }

  def fetchVideoDetails(
    videosIds      : List[String],
    minimumDuration: Long
  ): F[List[VideoDetails]] = {
    if (videosIds.isEmpty) {
      logInfo("No videos to fetch").as(Nil)
    } else {
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
            VideoDetails(
              id = item.id,
              channelId = item.snippet.channelId,
              publishedAt = item.snippet.publishedAt,
              views = item.statistics.viewCount.getOrElse(0),
              duration = Duration.parse(item.contentDetails.duration).getSeconds
            )
          }.filter(_.duration >= minimumDuration).pure
        }
        .handleErrorWith { e =>
          logger.error(e)(s"Error fetching video details: ${e.getMessage}").as(List.empty[VideoDetails])
        }
    }
  }
}

object YouTubeFetcher {
  def make[F[_] : Async : Network](
    applicationConfig     : ApplicationConfig,
    calculatedStateService: CalculatedStateService[F]
  ): Fetcher[F] = (_: LocalDateTime) =>
    MkHttpClient.forAsync[F].newEmber(applicationConfig.http4s.client).use { implicit client =>
      implicit val logger: SelfAwareStructuredLogger[F] = Slf4jLogger.getLoggerFromClass(YouTubeFetcher.getClass)
      val config = applicationConfig.youtubeDaemon
      val searchInformation = config.searchInformation

      for {
        latticeApiUrl <- config.usersSourceApiUrl.toOptionT.getOrRaise(new Exception(s"Could not get usersSourceApiUrl"))
        baseUrl <- config.youtubeApiUrl.toOptionT.getOrRaise(new Exception(s"Could not get YouTube Data API baseUrl"))
        apiKey <- config.youtubeApiKey.toOptionT.getOrRaise(new Exception(s"Could not get YouTube apiKey"))
        latticeUsersFetcher = new LatticeFetcher[F](latticeApiUrl)
        youtubeFetcher = new YouTubeFetcher[F](apiKey, baseUrl)

        calculatedState <- calculatedStateService.get
        dataSource: YouTubeDataSource = calculatedState.state.dataSources
          .get(DataSourceType.YouTube)
          .collect { case ds: YouTubeDataSource => ds }
          .getOrElse(YouTubeDataSource(Map.empty))

        latticeUsers <- latticeUsersFetcher.fetchLatticeUsersWithYouTubeAccount()
        filteredLatticeUsers = filterLatticeUsers(latticeUsers, searchInformation, dataSource, None)

        _ <- logInfo(
          s"YouTube Lattice fetcher started. " +
            s"\n\tTotal Lattice users: ${latticeUsers.length}, " +
            s"\n\tFiltered: ${filteredLatticeUsers.length}"
        )

        updates <- searchInformation.traverse { searchInfo =>
          for {
            _ <- logger.info(s"Searching for videos with search string ${searchInfo.text}")
            globalSearchResult <- youtubeFetcher.searchVideos(
              s"constellation-${searchInfo.text}",
              searchInfo.maxPerDay,
              Some(LocalDateTime.now().minusHours(searchInfo.publishedWithinHours.toHours))
            )

            channelIds = filteredLatticeUsers.flatMap(_.linkedAccounts.get.youtube.map(_.channelId))
            videoIdsFromSearch = youtubeFetcher.filterVideosByChannels(globalSearchResult, channelIds)

            _ <- logger.info("Getting pending videos to check")
            pendingVideos = getAllPendingVideosIds(dataSource, searchInfo)
            allVideosToGetUpdates = videoIdsFromSearch.filterNot(pendingVideos.contains) ++ pendingVideos

            dataUpdates <- youtubeFetcher.fetchVideoUpdates(filteredLatticeUsers, allVideosToGetUpdates, searchInfo)
          } yield dataUpdates
        }.map(_.flatten)

        filteredUpdates = updates.filter { update =>
          validateIfAddressCanProceed(dataSource, searchInformation, update.address, Some(update.video))
        }
      } yield filteredUpdates
    }

  def getAllPendingVideosIds(
    dataSource: YouTubeDataSource,
    searchInfo: YouTubeSearchInfo
  ) = {
    dataSource.existingWallets.values.flatMap { ytDataSourceAddress =>
      ytDataSourceAddress.addressRewards
        .get(searchInfo.text)
        .fold(List.empty[String]) { rewardDetails =>
          rewardDetails
            .rewardCandidates
            .map(_.map(_.id))
            .getOrElse(List.empty[String])
        }
    }.toList
  }

  def logInfo[F[_] : Async : Network](
    message: String
  )(implicit logger: SelfAwareStructuredLogger[F]
  ): F[Unit] =
    logger.info(s"[YouTubeFetcher] $message").flatMap(_ => Async[F].unit)

  def filterLatticeUsers(
    latticeUsers     : List[LatticeUser],
    searchInformation: List[SearchInfo],
    dataSource       : YouTubeDataSource,
    maybeVideo       : Option[VideoDetails]
  ): List[LatticeUser] = latticeUsers.filter { user =>
    user.primaryDagAddress.flatMap { address =>
      user.linkedAccounts.get.youtube.map { _ =>
        validateIfAddressCanProceed(dataSource, searchInformation, address, maybeVideo)
      }
    }.getOrElse(false)
  }

  private def validateIfAddressCanProceed(
    dataSource       : YouTubeDataSource,
    searchInformation: List[SearchInfo],
    address          : Address,
    maybeVideo       : Option[VideoDetails]
  ): Boolean =
    dataSource.existingWallets.get(address).fold(true) { wallet =>
      val videoNotRewarded = maybeVideo.fold(true) { video =>
        !searchInformation.exists { searchInfo =>
          wallet.addressRewards
            .get(searchInfo.text.toLowerCase)
            .exists(_.rewardedVideos.contains(video))
        }
      }

      videoNotRewarded && isWithinDailyLimit(searchInformation, wallet)
    }
}
