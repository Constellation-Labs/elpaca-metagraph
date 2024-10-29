package org.elpaca_metagraph.shared_data.daemons.fetcher

import cats.effect.Async
import cats.syntax.all._
import fs2.io.net.Network
import org.elpaca_metagraph.shared_data.app.ApplicationConfig
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

  def fetchVideosByChannelId(
    channelId: String,
    searchString: String,
    minimumDuration: Long,
    minimumViews: Long,
    publishedAfter: Option[LocalDateTime] = None,
    pageToken: Option[String] = None,
    result: List[VideoDetails] = List.empty
  ): F[List[VideoDetails]] = {
    val formattedPublishAfter = publishedAfter.map(_.atZone(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT))
    val request = Request[F](Method.GET, Uri.unsafeFromString(baseUrl.toString() + "/search")
      .withQueryParam("key", apiKey)
      .withQueryParam("channelId", channelId)
      .withQueryParam("q", searchString)
      .withQueryParam("type", "video")
      .withQueryParam("maxResults", 50)
      .withOptionQueryParam("publishedAfter", formattedPublishAfter)
      .withOptionQueryParam("pageToken", pageToken))

    client.expect[SearchListResponse](request)(jsonOf[F, SearchListResponse]).flatMap { response =>
      val videosIds = response.items.map(_.id.videoId)

      fetchVideoDetails(videosIds).flatMap { videos =>
        val updatedVideos = result ++ videos.filter(v => v.duration > minimumDuration && v.viewCount > minimumViews)

        response.nextPageToken match {
          case Some(token) => fetchVideosByChannelId(
            channelId,
            searchString,
            minimumDuration,
            minimumViews,
            publishedAfter,
            Some(token),
            updatedVideos
          )
          case None => updatedVideos.sortBy(_.publishedAt).pure
        }
      }
    }
  }

  private def fetchVideoDetails(
    videosIds: List[String]
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
            item.id,
            item.statistics.viewCount,
            Duration.parse(item.contentDetails.duration).getSeconds,
            item.snippet.publishedAt
          )
        ).pure
      }
    }
}

object YouTubeFetcher {
  def make[F[_] : Async : Network](
    applicationConfig: ApplicationConfig,
    calculatedStateService: CalculatedStateService[F]
  ): Fetcher[F] = (currentDate: LocalDateTime) =>
    MkHttpClient.forAsync[F].newEmber(applicationConfig.http4s.client).use { implicit client =>
      val config = applicationConfig.youtubeDaemon

      for {
        latticeApiUrl <- config.usersSourceApiUrl.toOptionT.getOrRaise(new Exception(s"Could not get usersSourceApiUrl"))
        baseUrl <- config.youtubeApiUrl.toOptionT.getOrRaise(new Exception(s"Could not get YouTube Data API baseUrl"))
        apiKey <- config.youtubeApiKey.toOptionT.getOrRaise(new Exception(s"Could not get YouTube apiKey"))
        searchInformation = config.searchInformation
        youtubeFetcher = new YouTubeFetcher[F](apiKey, baseUrl)
        latticeUsers <- youtubeFetcher.fetchLatticeUsers(latticeApiUrl)
        calculatedState <- calculatedStateService.get
        dataSource: YouTubeDataSource = calculatedState.state.dataSources
          .get(DataSourceType.YouTube)
          .collect { case ds: YouTubeDataSource => ds }
          .getOrElse(YouTubeDataSource(Map.empty))

        filteredLatticeUsers = latticeUsers.filterNot { user =>
          dataSource.existingWallets.filter { wallet =>
            val (_, ytDataSourceAddress) = wallet
            ytDataSourceAddress.rewardsReceivedToday >= searchInformation.maxPerDay
          }
          .keys.toList.contains(user.primaryDagAddress.get)
        }

        dataUpdates <- filteredLatticeUsers.traverse { user =>
          youtubeFetcher.fetchVideosByChannelId(
            user.youtube.get.channelId,
            searchInformation.text,
            searchInformation.minimumDuration,
            searchInformation.minimumViews,
            dataSource.existingWallets
              .get(user.primaryDagAddress.get)
              .map { wallet =>
                val (_, latestVideoReward) = wallet.videoRewards.last
                latestVideoReward.publishDate
              }
          ).map(_.map { video => YouTubeUpdate(
            user.primaryDagAddress.get,
            searchInformation.text,
            video.id,
            LocalDateTime.ofInstant(video.publishedAt, ZoneOffset.UTC)
          )})
        }

        newlyLoadedLatticeUsers = latticeUsers.filterNot { user =>
          dataSource.existingWallets.keys.toList.contains(user.primaryDagAddress.get)
        }

        newUpdates <- newlyLoadedLatticeUsers.traverse { user =>
          youtubeFetcher.fetchVideosByChannelId(
            user.youtube.get.channelId,
            searchInformation.text,
            searchInformation.minimumDuration,
            searchInformation.minimumViews
          ).map(_.map { video => YouTubeUpdate(
            user.primaryDagAddress.get,
            searchInformation.text,
            video.id,
            LocalDateTime.ofInstant(video.publishedAt, ZoneOffset.UTC)
          )})
        }
      } yield dataUpdates.flatten ++ newUpdates.flatten
    }
}
