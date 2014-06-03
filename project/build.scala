import sbt._
import sbt.Keys._

object GooTool extends Build {

  // Please update the version each time you publish
  val gooVersion = "1.00"

  lazy val gooTool = Project("frontend-goo-tool", file("tool"))
    .settings(
      organization := "com.gu",
      publishTo := Some(Resolver.file("local maven repo", file("my-maven-repo"))),
      scalaVersion := "2.10.3",
      version  := gooVersion,
      libraryDependencies ++= Seq(
        "args4j" % "args4j" % "2.0.26",
        "com.amazonaws" % "aws-java-sdk" % "1.7.1",
        "org.yaml" % "snakeyaml" % "1.13",
        "net.databinder.dispatch" % "dispatch-core_2.10" % "0.11.0",
        "com.fasterxml.jackson.module" % "jackson-module-scala_2.10" % "2.3.0",
        "org.slf4j" % "slf4j-simple" % "1.6.2"
      )
    )

  lazy val gooClient = Project("goo-client", file("client"))
    .settings(
      scalaVersion := "2.10.3",
      resolvers += "Guardian Github Releases" at "http://guardian.github.com/maven/repo-releases",
      // Replace the releases resolver with the local one below during development.
      //resolvers += "local maven repo" at "file://" + file("my-maven-repo").getAbsoluteFile().toString(),
      libraryDependencies += "com.gu" %% "frontend-goo-tool" % "latest.integration"
    )
}
