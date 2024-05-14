import sbt.*

object Dependencies {

  object V {
    val tessellation = "2.6.2"
    val decline = "2.4.1"
  }

  def tessellation(artifact: String): ModuleID = "org.constellation" %% s"tessellation-$artifact" % V.tessellation

  def decline(artifact: String = ""): ModuleID =
    "com.monovore" %% {
      if (artifact.isEmpty) "decline" else s"decline-$artifact"
    } % V.decline

  object Libraries {
    val tessellationNodeShared = tessellation("node-shared")
    val tessellationCurrencyL0 = tessellation("currency-l0")
    val tessellationCurrencyL1 = tessellation("currency-l1")
    val declineCore = decline()
    val declineEffect = decline("effect")
    val declineRefined = decline("refined")
    val requests = "com.lihaoyi" %% "requests" % "0.8.0"
    val upickle = "com.lihaoyi" %% "upickle" % "3.1.3"
    val catsEffectTestkit = "org.typelevel" %% "cats-effect-testkit" % "3.4.7"
    val weaverCats = "com.disneystreaming" %% "weaver-cats" % "0.8.1"
    val weaverDiscipline = "com.disneystreaming" %% "weaver-discipline" % "0.8.1"
    val weaverScalaCheck = "com.disneystreaming" %% "weaver-scalacheck" % "0.8.1"
  }


  // Scalafix rules
  val organizeImports = "com.github.liancheng" %% "organize-imports" % "0.5.0"

  object CompilerPlugin {

    val betterMonadicFor = compilerPlugin(
      "com.olegpy" %% "better-monadic-for" % "0.3.1"
    )

    val kindProjector = compilerPlugin(
      ("org.typelevel" % "kind-projector" % "0.13.3").cross(CrossVersion.full)
    )

    val semanticDB = compilerPlugin(
      ("org.scalameta" % "semanticdb-scalac" % "4.9.3").cross(CrossVersion.full)
    )
  }
}
