package org.elpaca_metagraph.shared_data.types

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import org.elpaca_metagraph.shared_data.types.Lattice._
import org.elpaca_metagraph.shared_data.types.YouTube.YouTubeDataAPI.VideoDetails
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
    dailyPostsNumber     : Long,
    videos               : List[VideoDetails],
    rewardCandidates     : Option[List[VideoDetails]] = None
  ) extends RewardInfo

  @derive(encoder, decoder)
  case class YouTubeDataSourceAddress(
    addressRewards: ListMap[String, YouTubeRewardInfo] = ListMap.empty
  ) extends SocialDataSourceAddress

  object YouTubeDataAPI {
    @derive(encoder, decoder)
    case class Id(
      videoId: String
    )

    @derive(encoder, decoder)
    case class VideoSummary(
      id     : Id,
      snippet: VideoSnippetResponse
    )

    @derive(encoder, decoder)
    case class VideoDetails(
      id         : String,
      channelId  : String,
      publishedAt: Instant,
      views      : Long,
      duration   : Long,
      checkUntil : Option[EpochProgress] = None
    )

    @derive(encoder, decoder)
    case class PageInfo(
      totalResults: Int
    )

    @derive(encoder, decoder)
    case class SearchListResponse(
      items        : List[VideoSummary],
      nextPageToken: Option[String],
      pageInfo     : PageInfo
    )

    @derive(encoder, decoder)
    case class VideoSnippetResponse(
      channelId  : String,
      publishedAt: Instant
    )

    @derive(encoder, decoder)
    case class VideoStatisticsResponse(
      viewCount: Option[Long]
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
