import com.typesafe.sbt.SbtNativePackager._
import sbt.Keys._
import sbt._

object GooTool extends Build {

  // It should always be a snapshot because Sbt will always run update on snapshots.
  val gooVersion = "3.4-SNAPSHOT"

  lazy val gooTool = Project("frontend-goo-tool", file("tool"))
    .settings(
      organization := "com.gu",
      version  := gooVersion,
      libraryDependencies ++= Seq(
        "args4j" % "args4j" % "2.0.26",
        "com.amazonaws" % "aws-java-sdk" % "1.8.9.1",
        "net.databinder.dispatch" %% "dispatch-core" % "0.11.0",
        "com.typesafe.play" %% "play-json" % "2.3.2",
        "org.slf4j" % "slf4j-simple" % "1.6.2",
        "org.eclipse.jetty" % "jetty-webapp" % "9.2.2.v20140723"
      ),
      resolvers := Seq(
        Classpaths.typesafeResolver,
        "Typesafe Repo" at "http://repo.typesafe.com/typesafe/releases/"
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
      resolvers := Seq(
        Classpaths.typesafeResolver,
        "Typesafe Repo" at "http://repo.typesafe.com/typesafe/releases/",
        "Guardian Github Snapshot" at "http://guardian.github.com/maven/repo-snapshots"),
      libraryDependencies += "com.gu" %% "frontend-goo-tool" % gooVersion
    )
    .settings(packageArchetype.java_application:_*)

  lazy val gooDevBuild = Project("dev-build", file("."))
    .dependsOn(gooTool)
}
