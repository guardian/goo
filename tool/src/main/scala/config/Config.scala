package goo

import com.amazonaws.auth.{AWSCredentialsProviderChain, AWSCredentials}
import com.amazonaws.internal.StaticCredentialsProvider
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.Constructor
import java.util.{Map => JavaMap}
import collection.mutable.{Map => MutableMap}
import scala.util.control.Exception.allCatch
import scala.collection.JavaConversions._
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

  lazy val awsUserCredentials: Option[AWSCredentialsProviderChain] =
    getFogConfig("default").flatMap {
      credentials =>
        for (
          accessKey <- credentials.get("aws_access_key_id");
          secretKey <- credentials.get("aws_secret_access_key")
        ) yield new FogAWSCredentials(accessKey, secretKey)
    }.map {
      x => new AWSCredentialsProviderChain(new StaticCredentialsProvider(x))
    }

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

  private def getFogConfig(group: String): Option[Map[String, String]] = {
    val fileConfig: Option[Map[String, Map[String, String]]] =
      allCatch either scala.io.Source.fromFile(System.getProperty("user.home") + "/.fog") match {
        case Right(fogConfig) =>
          val yaml = new Yaml(new Constructor(classOf[JavaMap[String, JavaMap[String, String]]]))
          val map = yaml.load(fogConfig.mkString).asInstanceOf[JavaMap[String, JavaMap[String, String]]].toMap
            .map {case (k,v)=> k->v.toMap}
          fogConfig.close()
          Some(map)
        case Left(e) =>
          println(s"Failed to load ~/.fog configuration")
          None
      }

    for {
      config <- fileConfig
      parsedMap <- config.get(group)
    } yield parsedMap
  }
}

class FogAWSCredentials(accessKeyId: String, secretKey: String) extends AWSCredentials {
  def getAWSAccessKeyId: String = accessKeyId

  def getAWSSecretKey: String = secretKey
}
