package goo.fastly

import java.io.File

import org.kohsuke.args4j.Argument
import org.kohsuke.args4j.{Option => ArgOption}
import org.kohsuke.args4j.spi.{SubCommand, SubCommands}
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.{ObjectListing, GetObjectRequest}
import collection.JavaConversions._
import scala.util.control.Exception.allCatch

import goo.{Config, Command, GooSubCommandHandler}

class FastlyCommand() extends Command {

  @Argument(handler = classOf[GooSubCommandHandler])
  @SubCommands(value = Array(new SubCommand(name = "logs", impl = classOf[LogsCommand]),
                             new SubCommand(name = "ls", impl = classOf[LsCommand]),
                             new SubCommand(name = "partition", impl = classOf[PartitionCommand])))
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
  implicit lazy val s3Client: AmazonS3Client = new AmazonS3Client(Config.awsUserCredentials)

  val bucketName = "aws-frontend-logs"

  def mapObjects(prefix: String, apply:(String => Unit)) {

    def expandObjectListing(listing: ObjectListing): List[String] = {

      val objects = listing.getObjectSummaries.map(_.getKey).toList
      objects.map(apply)

      if (listing.isTruncated) {
        objects ++ expandObjectListing(s3Client.listNextBatchOfObjects(listing))
      } else {
        Nil
      }
    }

    val result = allCatch either {
      val objectListing = s3Client.listObjects(bucketName, prefix)
      expandObjectListing(objectListing)
    }

    result match {
      case Right(_) => Unit
      case Left(ex) => println(s"Error: ${ex.getMessage}")
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

  @ArgOption(name="-f", usage="truncate the key name to just the filename")
  private val filenameOnly: Boolean = false;

  override def executeImpl() {

    val output = new File (outputDir)

    val validDirectory = if (output.exists && output.isDirectory) {
      true
    } else {
      println(s"Directory does not exist: $outputDir")
      false
    }

    if (validDirectory) {
      Fastly.mapObjects(s"fastly/$serviceName/$logNameFilter", downloadObject)
      Fastly.s3Client.shutdown()
    }
  }

  private def downloadObject(key: String) {
    println(s"Downloading $key")

    val outputFile = new File(outputDir, if (filenameOnly) key.split("/").last else key)

    if (outputFile.exists) {
      outputFile.delete()
    }

    val result = allCatch either Fastly.s3Client.getObject(new GetObjectRequest(Fastly.bucketName, key), outputFile);

    result match {
      case Left(ex) => println(s"Error: ${ex.getMessage}")
      case _ =>
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
    Fastly.mapObjects(s"fastly/$serviceName/$logNameFilter", println)
    Fastly.s3Client.shutdown()
  }
}

class PartitionCommand() extends Command {

  @Argument(multiValued = false, metaVar = "log name filter", usage = "log name filter", required = true, index = 0)
  private val logNameFilter: String = ""

  @Argument(multiValued = false, metaVar = "service name", usage = "fastly service name", required = false, index = 1)
  private val serviceName: String = "www.theguardian.com"

  val partitionedBucketName = "aws-frontend-logs-partitioned"

  override def executeImpl() {

    Fastly.mapObjects(s"fastly/$serviceName/$logNameFilter", copyAndRenameObject)
    Fastly.s3Client.shutdown()

  }

  private def copyAndRenameObject(key: String) {
    val targetKey = key.replaceFirst("T", "/").replaceFirst(":", "/").replaceFirst(":", "/")

    println(s"Moving $key to $partitionedBucketName/$targetKey")

    val result = allCatch either Fastly.s3Client.copyObject(Fastly.bucketName, key, partitionedBucketName, targetKey);

    result match {
      case Left(ex) => println(s"Error: ${ex.getMessage}")
      case _ =>
    }
  }
}
