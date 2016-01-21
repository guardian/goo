package goo

import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.auth.{SystemPropertiesCredentialsProvider, EnvironmentVariableCredentialsProvider, AWSCredentialsProviderChain, AWSCredentials}
import scala.util.control.Exception.allCatch
import scala.concurrent.duration._

object Config {

  object gOAuth {
    val id = "1069900237143-erdgcd1bd6i7gac1li3quhsisa8rtjgi"
    val clientId = s"$id.apps.googleusercontent.com"
    val emailAddress = s"$id@developer.gserviceaccount.com"
    val clientSecret = "AYunjb73YX_MAcUfwae9DYPe"

    val port = 8080
    val callbackUri: String = "/oauth2callback"
    val timeout = 30.seconds
    val redirectUrl = s"http://localhost:$port$callbackUri"
  }

  object Aws {
    val roleArn = "arn:aws:iam::642631414762:role/frontend-devs"
    val roleName = "frontend-devs"
    val credentialsLocation = System.getProperty("user.home") + "/.aws/credentials"
  }

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
