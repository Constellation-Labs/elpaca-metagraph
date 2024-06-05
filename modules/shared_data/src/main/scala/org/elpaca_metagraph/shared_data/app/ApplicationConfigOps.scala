package org.elpaca_metagraph.shared_data.app

import cats.effect.kernel.Sync
import ciris.Secret
import com.comcast.ip4s.{Host, Port}
import fs2.io.file.Path
import org.tessellation.node.shared.config.types.HttpClientConfig
import pureconfig._
import pureconfig.error.CannotConvert
import pureconfig.generic.semiauto.deriveReader
import pureconfig.module.catseffect.syntax._

object ApplicationConfigOps {

  import ConfigReaders._

  def readDefault[F[_] : Sync]: F[ApplicationConfig] =
    ConfigSource.default
      .loadF[F, ApplicationConfig]()
}

object ConfigReaders {

  implicit val secretReader: ConfigReader[Secret[String]] = ConfigReader[String].map(Secret(_))
  implicit val pathReader: ConfigReader[Path] = ConfigReader[String].map(Path(_))

  implicit val hostReader: ConfigReader[Host] =
    ConfigReader[String].emap(s => Host.fromString(s).toRight(CannotConvert(s, "Host", "Parse resulted in None")))

  implicit val portReader: ConfigReader[Port] =
    ConfigReader[Int].emap(i => Port.fromInt(i).toRight(CannotConvert(i.toString, "Port", "Parse resulted in None")))

  implicit val simplexDaemonConfigReader: ConfigReader[ApplicationConfig.SimplexDaemonConfig] = deriveReader
  implicit val exolixDaemonConfigReader: ConfigReader[ApplicationConfig.ExolixDaemonConfig] = deriveReader
  implicit val integrationnetNodesOperatorsDaemonConfigReader: ConfigReader[ApplicationConfig.IntegrationnetNodesOperatorsDaemonConfig] = deriveReader
  implicit val walletCreationDaemonConfigReader: ConfigReader[ApplicationConfig.WalletCreationDaemonConfig] = deriveReader
  implicit val nodeKeyReader: ConfigReader[ApplicationConfig.NodeKey] = deriveReader
  implicit val clientConfigReader: ConfigReader[HttpClientConfig] = deriveReader
  implicit val http4sConfigReader: ConfigReader[ApplicationConfig.Http4sConfig] = deriveReader
  implicit val applicationConfigReader: ConfigReader[ApplicationConfig] = deriveReader
}
