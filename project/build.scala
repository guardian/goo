import com.typesafe.sbt.GitVersioning
import com.typesafe.sbt.SbtNativePackager._
import sbt.Keys._
import sbt._
import bintray.BintrayKeys._

object GooTool extends Build {

  val awsVersion = "1.10.49"

  lazy val gooTool = Project("frontend-goo-tool", file("tool"))
    .settings(
      organization := "com.gu",
      libraryDependencies ++= Seq(
        "args4j" % "args4j" % "2.0.26",
        "com.amazonaws" % "aws-java-sdk-s3" % awsVersion,
        "com.amazonaws" % "aws-java-sdk-cloudformation" % awsVersion,
        "com.amazonaws" % "aws-java-sdk-ec2" % awsVersion,
        "com.amazonaws" % "aws-java-sdk-core" % awsVersion,
        "com.amazonaws" % "aws-java-sdk-autoscaling" % awsVersion,
        "com.amazonaws" % "aws-java-sdk-cloudwatch" % awsVersion,
        "com.amazonaws" % "aws-java-sdk-logs" % awsVersion,
        "net.databinder.dispatch" %% "dispatch-core" % "0.11.0",
        "com.typesafe.play" %% "play-json" % "2.3.2",
        "org.slf4j" % "slf4j-simple" % "1.6.2"
      ),
      resolvers := Seq(
        Classpaths.typesafeReleases,
        "Typesafe Repo" at "http://repo.typesafe.com/typesafe/releases/"
      ),
      bintrayOrganization := Some("guardian"),
      bintrayRepository := "frontend",
      licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0.html"))
    )
    .enablePlugins(GitVersioning)

  lazy val gooClient = Project("goo-client", file("client"))
    .settings(
      resolvers := Seq(
        Classpaths.typesafeReleases,
        "Typesafe Repo" at "http://repo.typesafe.com/typesafe/releases/",
        Resolver.bintrayRepo("guardian", "frontend")),
      libraryDependencies += "com.gu" %% "frontend-goo-tool" % "latest.integration"
    )
    .settings(packageArchetype.java_application:_*)

  lazy val gooDevBuild = Project("dev-build", file("."))
    .dependsOn(gooTool)
}
