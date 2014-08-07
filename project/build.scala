import sbt._
import sbt.Keys._

object GooTool extends Build {

  // It should always be a snapshot because Sbt will always run update on snapshots.
  val gooVersion = "3.4-SNAPSHOT"

  lazy val gooTool = Project("frontend-goo-tool", file("tool"))
    .settings(
      organization := "com.gu",
      version  := gooVersion,
      libraryDependencies ++= Seq(
        "args4j" % "args4j" % "2.0.26",
        "com.amazonaws" % "aws-java-sdk" % "1.7.1",
        "org.yaml" % "snakeyaml" % "1.13",
        "net.databinder.dispatch" %% "dispatch-core" % "0.11.0",
        "com.typesafe.play" %% "play-json" % "2.2.1",
        "org.slf4j" % "slf4j-simple" % "1.6.2"
      ),
      resolvers := Seq(
        "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/",
        "Templemore Repository" at "http://templemore.co.uk/repo",
        "Sonatype OSS" at "https://oss.sonatype.org/content/repositories/releases/",
        Classpaths.typesafeResolver
      ),
      publishTo <<= version { version: String =>
        val publishType = if (version.endsWith("SNAPSHOT")) "snapshots" else "releases"
        Some(Resolver.file(
          "Guardian Github " + publishType,
          file(System.getProperty("user.home") + "/guardian.github.com/maven/repo-" + publishType)
        ))
      }
    )

  lazy val gooClient = Project("goo-client", file("client"))
    .settings(
      resolvers += "Guardian Github Snapshot" at "http://guardian.github.com/maven/repo-snapshots",
      libraryDependencies += "com.gu" %% "frontend-goo-tool" % gooVersion
    )

  lazy val gooDevBuild = Project("dev-build", file("."))
    .dependsOn(gooTool)
}
