package org.elpaca_metagraph.shared_data.app

import ciris.Secret
import fs2.io.file.Path
import org.elpaca_metagraph.shared_data.types.Refined.ApiUrl
import org.tessellation.node.shared.config.types.HttpClientConfig
import org.tessellation.schema.ID.Id
import org.tessellation.schema.address.Address
import org.tessellation.schema.balance.Amount

import java.time.LocalDate
import scala.concurrent.duration._

case class ApplicationConfig(
  http4s                            : ApplicationConfig.Http4sConfig,
  dataApi                           : ApplicationConfig.DataApiConfig,
  exolixDaemon                      : ApplicationConfig.ExolixDaemonConfig,
  simplexDaemon                     : ApplicationConfig.SimplexDaemonConfig,
  integrationnetNodesOperatorsDaemon: ApplicationConfig.IntegrationnetNodesOperatorsDaemonConfig,
  walletCreationHoldingDagDaemon    : ApplicationConfig.WalletCreationHoldingDagDaemonConfig,
  inflowTransactionsDaemon          : ApplicationConfig.InflowTransactionsDaemonConfig,
  outflowTransactionsDaemon         : ApplicationConfig.OutflowTransactionsDaemonConfig,
  nodeKey                           : ApplicationConfig.NodeKey,
  xDaemon                           : ApplicationConfig.XDaemonConfig,
  streak                            : ApplicationConfig.StreakConfig,
  youtubeDaemon                     : ApplicationConfig.YouTubeDaemonConfig
)

object ApplicationConfig {

  case class Http4sConfig(
    client: HttpClientConfig,
  )

  case class DataApiConfig(
    ip  : String,
    port: Int,
  )

  case class ExolixDaemonConfig(
    idleTime: FiniteDuration,
    apiKey  : Option[String],
    apiUrl  : Option[ApiUrl]
  )

  case class SimplexDaemonConfig(
    idleTime: FiniteDuration,
    apiKey  : Option[String],
    apiUrl  : Option[ApiUrl],
  )

  case class IntegrationnetNodesOperatorsDaemonConfig(
    idleTime: FiniteDuration,
    apiKey  : Option[String],
    apiUrl  : Option[ApiUrl]
  )

  case class WalletCreationHoldingDagDaemonConfig(
    idleTime: FiniteDuration,
    apiUrl  : Option[ApiUrl]
  )

  case class WalletsInfo(
    address      : Address,
    minimumAmount: Amount,
    rewardAmount : Amount,
    afterDate    : LocalDate
  )

  case class InflowTransactionsDaemonConfig(
    idleTime   : FiniteDuration,
    apiUrl     : Option[ApiUrl],
    walletsInfo: List[WalletsInfo]
  )

  case class OutflowTransactionsDaemonConfig(
    idleTime   : FiniteDuration,
    apiUrl     : Option[ApiUrl],
    walletsInfo: List[WalletsInfo]
  )

  trait SearchInfo {
    def text        : String
    def rewardAmount: Amount
    def maxPerDay   : Long
  }

  case class XSearchInfo(
    text        : String,
    rewardAmount: Amount,
    maxPerDay   : Long
  ) extends SearchInfo

  case class XDaemonConfig(
    idleTime          : FiniteDuration,
    usersSourceApiUrl : Option[ApiUrl],
    xApiUrl           : Option[ApiUrl],
    xApiConsumerKey   : Option[String],
    xApiConsumerSecret: Option[String],
    xApiAccessToken   : Option[String],
    xApiAccessSecret  : Option[String],
    searchInformation : List[XSearchInfo]
  )

  case class StreakConfig(
    stargazerPublicKey: Id
  )

  case class YouTubeSearchInfo(
    text                     : String,
    rewardAmount             : Amount,
    maxPerDay                : Long,
    minimumViews             : Long,
    minimumDuration          : FiniteDuration,
    publishedWithinHours     : FiniteDuration,
    daysToMonitorVideoUpdates: FiniteDuration
  ) extends SearchInfo

  case class YouTubeDaemonConfig(
    idleTime          : FiniteDuration,
    usersSourceApiUrl : Option[ApiUrl],
    youtubeApiUrl     : Option[ApiUrl],
    youtubeApiKey     : Option[String],
    searchInformation : List[YouTubeSearchInfo]
  )

  case class NodeKey(
    keystore: Path,
    alias   : Secret[String],
    password: Secret[String]
  )

}
