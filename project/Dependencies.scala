import sbt.*

object Dependencies {

  object V {
    val tessellation = "3.0.0-rc.3"
    val decline = "2.4.1"
  }

  def tessellation(artifact: String): ModuleID = "io.constellationnetwork" %% s"tessellation-$artifact" % V.tessellation

  def decline(artifact: String = ""): ModuleID =
    "com.monovore" %% {
      if (artifact.isEmpty) "decline" else s"decline-$artifact"
    } % V.decline

  object Libraries {
    val tessellationSdk = tessellation("sdk")
    val declineCore = decline()
    val declineEffect = decline("effect")
    val declineRefined = decline("refined")
    val catsEffectTestkit = "org.typelevel" %% "cats-effect-testkit" % "3.4.7"
    val scalaTest =  "org.scalatest" %% "scalatest" % "3.2.19"
    val scribeJavaCore =  "com.github.scribejava" % "scribejava-core" % "8.3.2"
    val scribeJavaApis =  "com.github.scribejava" % "scribejava-apis" % "8.3.2"
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
