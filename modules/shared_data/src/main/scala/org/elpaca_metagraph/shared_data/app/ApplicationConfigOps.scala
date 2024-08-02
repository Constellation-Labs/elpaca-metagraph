package org.elpaca_metagraph.shared_data.app

import cats.effect.kernel.Sync
import ciris.Secret
import com.comcast.ip4s.{Host, Port}
import eu.timepit.refined.refineV
import eu.timepit.refined.types.numeric.NonNegLong
import fs2.io.file.Path
import org.elpaca_metagraph.shared_data.types.Refined.ApiUrl
import org.tessellation.node.shared.config.types.HttpClientConfig
import org.tessellation.schema.address.{Address, DAGAddressRefined}
import org.tessellation.schema.balance.Amount
import pureconfig._
import pureconfig.error.CannotConvert
import pureconfig.generic.semiauto.deriveReader
import pureconfig.module.catseffect.syntax._

import java.time.LocalDate

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

  implicit val amountReader: ConfigReader[Amount] = {
    import eu.timepit.refined.pureconfig._
    ConfigReader[NonNegLong].map(Amount(_))
  }

  implicit val apiUrlReader: ConfigReader[ApiUrl] = ConfigReader[String].map(ApiUrl.unsafeFrom)


  implicit val localDateReader: ConfigReader[LocalDate] = ConfigReader[String].map(LocalDate.parse)
  implicit val addressReader: ConfigReader[Address] = ConfigReader[String].map(refineV[DAGAddressRefined](_).toOption.map(Address(_)).get)

  implicit val dataApiConfigReader: ConfigReader[ApplicationConfig.DataApiConfig] = deriveReader
  implicit val simplexDaemonConfigReader: ConfigReader[ApplicationConfig.SimplexDaemonConfig] = deriveReader
  implicit val exolixDaemonConfigReader: ConfigReader[ApplicationConfig.ExolixDaemonConfig] = deriveReader
  implicit val integrationnetNodesOperatorsDaemonConfigReader: ConfigReader[ApplicationConfig.IntegrationnetNodesOperatorsDaemonConfig] = deriveReader
  implicit val walletCreationHoldingDagDaemonConfigReader: ConfigReader[ApplicationConfig.WalletCreationHoldingDagDaemonConfig] = deriveReader
  implicit val walletsInfoReader: ConfigReader[ApplicationConfig.WalletsInfo] = deriveReader
  implicit val inflowTransactionsDaemonConfigReader: ConfigReader[ApplicationConfig.InflowTransactionsDaemonConfig] = deriveReader
  implicit val outflowTransactionsDaemonConfigReader: ConfigReader[ApplicationConfig.OutflowTransactionsDaemonConfig] = deriveReader
  implicit val xSearchInfoReader: ConfigReader[ApplicationConfig.XSearchInfo] = deriveReader
  implicit val xDaemonConfigReader: ConfigReader[ApplicationConfig.XDaemonConfig] = deriveReader
  implicit val nodeKeyReader: ConfigReader[ApplicationConfig.NodeKey] = deriveReader
  implicit val clientConfigReader: ConfigReader[HttpClientConfig] = deriveReader
  implicit val http4sConfigReader: ConfigReader[ApplicationConfig.Http4sConfig] = deriveReader
  implicit val applicationConfigReader: ConfigReader[ApplicationConfig] = deriveReader
}