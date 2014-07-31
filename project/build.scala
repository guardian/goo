import sbt._
import sbt.Keys._

object GooTool extends Build {

  // Please update the version each time you publish
  val gooVersion = "3.3-SNAPSHOT"

  lazy val gooTool = Project("frontend-goo-tool", file("tool"))
    .settings(
      organization := "com.gu",
      version  := gooVersion,
      libraryDependencies ++= Seq(
        "args4j" % "args4j" % "2.0.26",
        "com.amazonaws" % "aws-java-sdk" % "1.7.1",
        "org.yaml" % "snakeyaml" % "1.13",
        "net.databinder.dispatch" % "dispatch-core_2.10" % "0.11.0",
        "com.fasterxml.jackson.module" % "jackson-module-scala_2.10" % "2.3.0",
        "org.slf4j" % "slf4j-simple" % "1.6.2"
      ),
      publishTo <<= version { version: String =>
        val publishType = if (version.endsWith("SNAPSHOT")) "snapshots" else "releases"
        Some(
          Resolver.file(
            "guardian github " + publishType,
            file(System.getProperty("user.home") + "/guardian.github.com/maven/repo-" + publishType)
          )
        )
      }
    )

  lazy val gooClient = Project("goo-client", file("client"))
    .settings(
      resolvers += "Guardian Github Releases" at "http://guardian.github.com/maven/repo-releases",
      libraryDependencies += "com.gu" %% "frontend-goo-tool" % "latest.integration"
    )

  lazy val gooDevBuild = Project("dev-build", file("."))
    .dependsOn(gooTool)
}
