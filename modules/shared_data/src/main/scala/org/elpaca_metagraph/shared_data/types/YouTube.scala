package org.elpaca_metagraph.shared_data.types

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import org.elpaca_metagraph.shared_data.types.YouTube.YouTubeDataAPI.VideoDetails
import org.tessellation.schema.address.Address
import org.tessellation.schema.balance.Amount
import org.tessellation.schema.epoch.EpochProgress

import java.time.Instant
import scala.collection.immutable.ListMap

object YouTube {
  @derive(encoder, decoder)
  case class YouTubeRewardInfo(
    dailyEpochProgress   : EpochProgress,
    epochProgressToReward: EpochProgress,
    amountToReward       : Amount,
    searchText           : String,
    videos               : List[VideoDetails],
    dailyPostsNumber     : Long
  )

  @derive(encoder, decoder)
  case class YouTubeDataSourceAddress(
    addressRewards: ListMap[String, YouTubeRewardInfo] = ListMap.empty
  )

  object LatticeClient {
    @derive(encoder, decoder)
    case class YouTubeAccount(
      channelId: String
    )

    @derive(encoder, decoder)
    case class LatticeUser(
      id               : String,
      primaryDagAddress: Option[Address],
      youtube          : Option[YouTubeAccount]
    )

    @derive(encoder, decoder)
    case class LatticeUserMeta(
      total : Long,
      limit : Long,
      offset: Long
    )

    @derive(encoder, decoder)
    case class LatticeUsersApiResponse(
      data: List[LatticeUser],
      meta: Option[LatticeUserMeta])
    }

  object YouTubeDataAPI {
    @derive(encoder, decoder)
    case class Id(
      videoId: String
    )

    @derive(encoder, decoder)
    case class VideoId(
      id: Id
    )

    @derive(encoder, decoder)
    case class VideoDetails(
      id         : String,
      publishedAt: Instant,
      views      : Long,
      duration   : Long
    )

    @derive(encoder, decoder)
    case class SearchListResponse(
      items        : List[VideoId],
      nextPageToken: Option[String]
    )

    @derive(encoder, decoder)
    case class VideoSnippetResponse(
      publishedAt: Instant
    )

    @derive(encoder, decoder)
    case class VideoStatisticsResponse(
      viewCount: Long
    )

    @derive(encoder, decoder)
    case class VideoContentDetailsResponse(
      duration: String
    )

    @derive(encoder, decoder)
    case class VideoResponse(
      id            : String,
      snippet       : VideoSnippetResponse,
      statistics    : VideoStatisticsResponse,
      contentDetails: VideoContentDetailsResponse
    )

    @derive(encoder, decoder)
    case class VideoListResponse(
      items: List[VideoResponse]
    )
  }
}
