package commands.refreshAwsTokens

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

}
