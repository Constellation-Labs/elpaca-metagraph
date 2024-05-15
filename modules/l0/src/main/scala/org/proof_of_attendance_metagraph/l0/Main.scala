package org.proof_of_attendance_metagraph.l0

import cats.effect.{IO, Resource}
import cats.syntax.option._
import org.proof_of_attendance_metagraph.l0.rewards.ProofOfAttendanceRewards
import org.proof_of_attendance_metagraph.shared_data.types.codecs.JsonBinaryCodec
import org.tessellation.currency.dataApplication._
import org.tessellation.currency.l0.CurrencyL0App
import org.tessellation.currency.schema.currency.{CurrencyIncrementalSnapshot, CurrencySnapshotStateProof}
import org.tessellation.ext.cats.effect.ResourceIO
import org.tessellation.json.JsonSerializer
import org.tessellation.node.shared.domain.rewards.Rewards
import org.tessellation.node.shared.snapshot.currency.CurrencySnapshotEvent
import org.tessellation.schema.cluster.ClusterId
import org.tessellation.schema.semver.{MetagraphVersion, TessellationVersion}
import org.tessellation.security.SecurityProvider

import java.util.UUID

object Main extends CurrencyL0App(
  "currency-l0",
  "currency L0 node",
  ClusterId(UUID.fromString("517c3a05-9219-471b-a54c-21b7d72f4ae5")),
  tessellationVersion = TessellationVersion.unsafeFrom(org.tessellation.BuildInfo.version),
  metagraphVersion = MetagraphVersion.unsafeFrom(org.proof_of_attendance_metagraph.l0.BuildInfo.version)
) {

  override def dataApplication: Option[Resource[IO, BaseDataApplicationL0Service[IO]]] = (for {
    implicit0(json2bin: JsonSerializer[IO]) <- JsonBinaryCodec.forSync[IO].asResource

    l1Service <- MetagraphL0Service.make[IO]().asResource
  } yield l1Service).some

  override def rewards(implicit sp: SecurityProvider[IO]): Option[Rewards[IO, CurrencySnapshotStateProof, CurrencyIncrementalSnapshot, CurrencySnapshotEvent]] =
    ProofOfAttendanceRewards.make[IO]().some
}

