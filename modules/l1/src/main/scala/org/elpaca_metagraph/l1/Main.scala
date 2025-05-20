package org.elpaca_metagraph.l1

import io.constellationnetwork.currency.l1.CurrencyL1App
import io.constellationnetwork.schema.cluster.ClusterId
import io.constellationnetwork.schema.semver.{MetagraphVersion, TessellationVersion}

import java.util.UUID

object Main extends CurrencyL1App(
  "currency-l1",
  "currency L1 node",
  ClusterId(UUID.fromString("517c3a05-9219-471b-a54c-21b7d72f4ae5")),
  tessellationVersion = TessellationVersion.unsafeFrom(io.constellationnetwork.BuildInfo.version),
  metagraphVersion = MetagraphVersion.unsafeFrom(org.elpaca_metagraph.l1.BuildInfo.version)
) {}
