package org.elpaca_metagraph.data_l1

import cats.effect.std.Supervisor
import cats.effect.{IO, Resource}
import cats.syntax.all._
import org.elpaca_metagraph.shared_data.Utils.loadKeyPair
import org.elpaca_metagraph.shared_data.app.ApplicationConfigOps
import org.elpaca_metagraph.shared_data.daemons.DaemonApis
import org.elpaca_metagraph.shared_data.types.codecs.JsonBinaryCodec
import org.tessellation.currency.dataApplication._
import org.tessellation.currency.l1.CurrencyL1App
import org.tessellation.ext.cats.effect.ResourceIO
import org.tessellation.json.JsonSerializer
import org.tessellation.schema.cluster.ClusterId
import org.tessellation.schema.semver.{MetagraphVersion, TessellationVersion}
import org.tessellation.security.SecurityProvider

import java.util.UUID

object Main extends CurrencyL1App(
  "currency-data_l1",
  "currency data L1 node",
  ClusterId(UUID.fromString("517c3a05-9219-471b-a54c-21b7d72f4ae5")),
  tessellationVersion = TessellationVersion.unsafeFrom(org.tessellation.BuildInfo.version),
  metagraphVersion = MetagraphVersion.unsafeFrom(org.elpaca_metagraph.data_l1.BuildInfo.version)
) {

  override def dataApplication: Option[Resource[IO, BaseDataApplicationL1Service[IO]]] = (for {
    implicit0(supervisor: Supervisor[IO]) <- Supervisor[IO]
    implicit0(sp: SecurityProvider[IO]) <- SecurityProvider.forAsync[IO]
    implicit0(json2bin: JsonSerializer[IO]) <- JsonBinaryCodec.forSync[IO].asResource

    config <- ApplicationConfigOps.readDefault[IO].asResource
    keyPair <- loadKeyPair[IO](config).asResource
    _ <- DaemonApis.make[IO](config, keyPair).spawnL1Daemons.asResource
    l1Service <- DataL1Service.make[IO]().asResource
  } yield l1Service).some
}
