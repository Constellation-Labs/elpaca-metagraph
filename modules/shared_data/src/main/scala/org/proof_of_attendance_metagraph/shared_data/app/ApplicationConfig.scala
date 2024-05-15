package org.proof_of_attendance_metagraph.shared_data.app

import ciris.Secret
import fs2.io.file.Path
import org.tessellation.node.shared.config.types.HttpClientConfig

import scala.concurrent.duration._

case class ApplicationConfig(
  http4s                            : ApplicationConfig.Http4sConfig,
  exolixDaemon                      : ApplicationConfig.ExolixDaemonConfig,
  simplexDaemon                     : ApplicationConfig.SimplexDaemonConfig,
  twitterDaemon                     : ApplicationConfig.TwitterDaemonConfig,
  integrationnetNodesOperatorsDaemon: ApplicationConfig.IntegrationnetNodesOperatorsDaemonConfig,
  nodeKey                           : ApplicationConfig.NodeKey
)

object ApplicationConfig {

  case class Http4sConfig(
    client: HttpClientConfig,
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

  case class TwitterDaemonConfig(
    idleTime: FiniteDuration,
    apiKey  : Option[String],
    apiUrl  : Option[String]
  )

  case class IntegrationnetNodesOperatorsDaemonConfig(
    idleTime: FiniteDuration,
    apiKey  : Option[String],
    apiUrl  : Option[String]
  )

  case class NodeKey(
    keystore: Path,
    alias   : Secret[String],
    password: Secret[String]
  )

}
