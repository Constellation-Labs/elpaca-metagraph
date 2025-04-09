package org.elpaca_metagraph.l0

import cats.effect.std.Supervisor
import cats.effect.{IO, Resource}
import cats.syntax.all._
import org.elpaca_metagraph.l0.rewards.ElpacaRewards
import org.elpaca_metagraph.shared_data.Utils.loadKeyPair
import org.elpaca_metagraph.shared_data.app.ApplicationConfigOps
import org.elpaca_metagraph.shared_data.calculated_state.CalculatedStateService
import org.elpaca_metagraph.shared_data.daemons.DaemonApis
import org.elpaca_metagraph.shared_data.types.codecs.JsonBinaryCodec
import io.constellationnetwork.currency.dataApplication._
import io.constellationnetwork.currency.l0.CurrencyL0App
import io.constellationnetwork.currency.schema.currency.{CurrencyIncrementalSnapshot, CurrencySnapshotStateProof}
import io.constellationnetwork.ext.cats.effect.ResourceIO
import io.constellationnetwork.json.JsonSerializer
import io.constellationnetwork.node.shared.domain.rewards.Rewards
import io.constellationnetwork.node.shared.snapshot.currency.CurrencySnapshotEvent
import io.constellationnetwork.schema.cluster.ClusterId
import io.constellationnetwork.schema.semver.{MetagraphVersion, TessellationVersion}
import io.constellationnetwork.security.SecurityProvider

import java.util.UUID

object Main extends CurrencyL0App(
  "currency-l0",
  "currency L0 node",
  ClusterId(UUID.fromString("517c3a05-9219-471b-a54c-21b7d72f4ae5")),
  tessellationVersion = TessellationVersion.unsafeFrom(io.constellationnetwork.BuildInfo.version),
  metagraphVersion = MetagraphVersion.unsafeFrom(org.elpaca_metagraph.l0.BuildInfo.version)
) {

  override def dataApplication: Option[Resource[IO, BaseDataApplicationL0Service[IO]]] = (for {
    implicit0(supervisor: Supervisor[IO]) <- Supervisor[IO]
    implicit0(sp: SecurityProvider[IO]) <- SecurityProvider.forAsync[IO]
    implicit0(json2bin: JsonSerializer[IO]) <- JsonBinaryCodec.forSync[IO].asResource

    config <- ApplicationConfigOps.readDefault[IO].asResource
    keyPair <- loadKeyPair[IO](config).asResource
    calculatedStateService <- CalculatedStateService.make[IO].asResource
    _ <- DaemonApis.make[IO](config, keyPair).spawnL0Daemons(calculatedStateService).asResource
    l0Service <- MetagraphL0Service.make[IO](calculatedStateService, config).asResource
  } yield l0Service).some


  override def rewards(implicit sp: SecurityProvider[IO]): Option[Rewards[IO, CurrencySnapshotStateProof, CurrencyIncrementalSnapshot, CurrencySnapshotEvent]] =
    ElpacaRewards.make[IO]().some
}

