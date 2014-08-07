// Additional information on initialization
logLevel := Level.Warn

resolvers ++= Seq(
  Classpaths.typesafeReleases
)

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "0.7.1")