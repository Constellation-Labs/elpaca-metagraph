package org.elpaca_metagraph.shared_data.app

import ciris.Secret
import fs2.io.file.Path
import org.tessellation.node.shared.config.types.HttpClientConfig
import org.tessellation.schema.address.Address

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
  nodeKey                           : ApplicationConfig.NodeKey
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
    apiUrl  : Option[String]
  )

  case class SimplexDaemonConfig(
    idleTime: FiniteDuration,
    apiKey  : Option[String],
    apiUrl  : Option[String],
  )

  case class IntegrationnetNodesOperatorsDaemonConfig(
    idleTime: FiniteDuration,
    apiKey  : Option[String],
    apiUrl  : Option[String]
  )

  case class WalletCreationHoldingDagDaemonConfig(
    idleTime: FiniteDuration,
    apiUrl  : Option[String]
  )

  case class WalletsInfo(
    address      : Address,
    minimumAmount: Long,
    rewardAmount : Long,
    afterDate    : LocalDate
  )

  case class InflowTransactionsDaemonConfig(
    idleTime   : FiniteDuration,
    apiUrl     : Option[String],
    walletsInfo: List[WalletsInfo]
  )

  case class OutflowTransactionsDaemonConfig(
    idleTime   : FiniteDuration,
    apiUrl     : Option[String],
    walletsInfo: List[WalletsInfo]
  )

  case class NodeKey(
    keystore: Path,
    alias   : Secret[String],
    password: Secret[String]
  )

}
