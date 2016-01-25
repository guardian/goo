package goo

import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.auth.AWSCredentialsProviderChain
import scala.util.control.Exception.allCatch
import scala.concurrent.duration._

object Config {

  lazy val cloudformationHome = new java.io.File(System.getProperty("user.dir") + "/../cloudformation").getCanonicalPath

  lazy val awsUserCredentials: AWSCredentialsProviderChain =
    new AWSCredentialsProviderChain(
      new ProfileCredentialsProvider("frontend"),
      new ProfileCredentialsProvider("nextgen")
    )

  lazy val riffRaffKey: Option[String] =
    allCatch either scala.io.Source.fromFile(System.getProperty("user.home") + "/.riffraff") match {
      case Right(config) =>
        val key = config.mkString.trim
        config.close()
        Some(key)
      case Left(e) =>
        println(s"Failed to load ~/.riffraff key file. Go to riffraff to get your API key and write it into the key file.")
        None
    }
}
