package goo

import com.amazonaws.auth.{ AWSCredentialsProviderChain, AWSCredentials }
import com.amazonaws.internal.StaticCredentialsProvider
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.Constructor
import java.util.{ Map => JavaMap }
import collection.mutable.{ Map => MutableMap }
import collection.JavaConversions.mapAsScalaMap
import scala.reflect.BeanProperty
import scala.util.control.Exception.allCatch

object Config {

  lazy val cloudformationHome = new java.io.File(System.getProperty("user.dir") + "/../cloudformation").getCanonicalPath()

  lazy val awsUserCredentials: Option[AWSCredentialsProviderChain] = {

    getFogConfig("default").map( credentials => {
      val fogCredentials = new FogAWSCredentials(credentials._1, credentials._2)
      new AWSCredentialsProviderChain(new StaticCredentialsProvider(fogCredentials))
    })
  }

  lazy val riffRaffKey: Option[String] = {

    val result = allCatch either scala.io.Source.fromFile(System.getProperty("user.home") + "/.riffraff")

    result match {
      case Right(config) => {
        val key = config.mkString.trim
        config.close()
        Some(key)
      }
      case Left(e) => {
        println(s"Failed to load ~/.riffraff key file.")
        None
      }
    }
  }

  private def getFogConfig(group: String): Option[(String, String)] = {
    val result = allCatch either scala.io.Source.fromFile(System.getProperty("user.home") + "/.fog")
    val fileConfig = result match {
      case Right(fogConfig) => {
        val yaml = new Yaml(new Constructor(classOf[JavaMap[String, JavaMap[String, String]]]))
        val map = yaml.load(fogConfig.mkString).asInstanceOf[JavaMap[String, JavaMap[String,String]]]
        fogConfig.close()
        Some(mapAsScalaMap(map))
      }
      case Left(e) => {
        println(s"Failed to load ~/.fog configuration")
        None
      }
    }

    for {
      config <- fileConfig
      parsedMap <- config.get(group)
      default: MutableMap[String,String] <- Some(mapAsScalaMap(parsedMap))
      accessKey <- default.get("aws_access_key_id")
      secretKey <- default.get("aws_secret_access_key")
    } yield {
      (accessKey, secretKey)
    }
  }
}

class FogAWSCredentials(accessKeyId: String, secretKey: String) extends AWSCredentials {
  def getAWSAccessKeyId: String = accessKeyId
  def getAWSSecretKey: String = secretKey
}