package goo.fastly

import java.io.File

import org.kohsuke.args4j.Argument
import org.kohsuke.args4j.spi.{SubCommand, SubCommands}
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.GetObjectRequest
import collection.JavaConversions._
import scala.util.control.Exception.allCatch

import goo.{Config, Command, GooSubCommandHandler}

class FastlyCommand() extends Command {

  @Argument(handler = classOf[GooSubCommandHandler])
  @SubCommands(value = Array(new SubCommand(name = "logs", impl = classOf[LogsCommand]),
                             new SubCommand(name = "ls", impl = classOf[LsCommand])))
  private val cmd: Command = null

  override def executeImpl() {
    if (cmd != null) {
      cmd.execute()
    } else {
      println("Invalid fastly command")
    }
  }
}

object Fastly {
  lazy val s3Client: Option[AmazonS3Client] = {
    Config.awsUserCredentials.map { provider =>
      new AmazonS3Client(provider)
    }
  }
}

class LogsCommand() extends Command {

  @Argument(multiValued = false, metaVar = "log name filter", usage = "log name filter", required = true, index = 0)
  private val logNameFilter: String = ""

  @Argument(multiValued = false, metaVar = "output dir", usage = "output directory", required = true, index = 1)
  private val outputDir: String = ""

  @Argument(multiValued = false, metaVar = "service name", usage = "fastly service name", required = false, index = 2)
  private val serviceName: String = "www.theguardian.com"

  private val bucketName = "aws-frontend-logs"

  override def executeImpl() {

    val output = new File (outputDir)

    val validDirectory = if (output.exists && output.isDirectory) {
      true
    } else {
      println(s"Directory does not exist: $outputDir")
      false
    }

    for (client <- Fastly.s3Client if validDirectory) {
      implicit val s3client = client
      listObjects(logNameFilter).map(getObject)
      s3client.shutdown()
    }
  }

  private def getObject(key: String)(implicit client: AmazonS3Client) {
    println(s"Downloading $key")

    val outputFile = new File(outputDir, key)

    if (outputFile.exists) {
      outputFile.delete()
    }

    val result = allCatch either client.getObject(new GetObjectRequest(bucketName, key), outputFile);

    result match {
      case Left(ex) => println(s"Error: ${ex.getMessage}")
      case _ =>
    }
  }

  private def listObjects(filter: String)(implicit client: AmazonS3Client): List[String] = {

    val result = allCatch either client.listObjects(bucketName, s"fastly/$serviceName/$filter")
      .getObjectSummaries.map(_.getKey)

    result match {
      case Right(list) => list.toList
      case Left(ex) =>
        println(s"Error: ${ex.getMessage}")
        Nil
    }
  }
}

class LsCommand() extends Command {

  @Argument(multiValued = false, metaVar = "log name filter", usage = "log name filter", required = false, index = 0)
  private val logNameFilter: String = ""

  @Argument(multiValued = false, metaVar = "service name", usage = "fastly service name", required = false, index = 1)
  private val serviceName: String = "www.theguardian.com"

  private val bucketName = "aws-frontend-logs"

  override def executeImpl() {

    for (client <- Fastly.s3Client) {
      implicit val s3client = client
      listObjects(logNameFilter).map(println)
      s3client.shutdown()
    }
  }
  private def listObjects(filter: String)(implicit client: AmazonS3Client): List[String] = {

    val result = allCatch either client.listObjects(bucketName, s"fastly/$serviceName/$filter")
      .getObjectSummaries.map(_.getKey)

    result match {
      case Right(list) => list.toList
      case Left(ex) =>
        println(s"Error: ${ex.getMessage}")
        Nil
    }
  }
}

