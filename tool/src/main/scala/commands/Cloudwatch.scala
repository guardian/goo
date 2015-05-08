package commands

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths, StandardOpenOption}

import com.amazonaws.services.logs.AWSLogsAsyncClient
import com.amazonaws.services.logs.model._
import commands.Cloudwatch.LogsCommand
import goo.{Command, Config, GooSubCommandHandler}
import org.joda.time.DateTime
import org.kohsuke.args4j.Argument
import org.kohsuke.args4j.spi.{SubCommand, SubCommands}

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.sys.SystemProperties
import scala.util.control.NonFatal

class CloudwatchCommand() extends Command {

  @Argument(handler = classOf[GooSubCommandHandler])
  @SubCommands(value = Array(
    new SubCommand(name = "logs", impl = classOf[LogsCommand])
  ))
  private val cmd: Command = null

  override def executeImpl(): Unit = cmd.execute()
}

object Cloudwatch {

  class LogsCommand() extends Command {
    @Argument(handler = classOf[GooSubCommandHandler])
    @SubCommands(value = Array(
      new SubCommand(name = "download", impl = classOf[DownloadCommand])
    ))
    private val cmd: Command = null

    override def executeImpl(): Unit = cmd.execute()
  }

  class DownloadCommand() extends Command {

    @Argument(index = 0,
      required = true,
      metaVar = "log-group-name",
      usage = "Cloudwatch log group name")
    private val logGroupName: String = ""

    @Argument(index = 1,
      required = true,
      metaVar = "start-time",
      usage = "Time to fetch from")
    private val startTimeStr: String = ""
    private lazy val startTime = DateTime.parse(startTimeStr)

    @Argument(index = 2,
      required = true,
      metaVar = "end-time",
      usage = "Time to fetch to")
    private val endTimeStr: String = ""
    private lazy val endTime = DateTime.parse(endTimeStr)

    @Argument(index = 3,
      required = false,
      metaVar = "output-dir",
      usage = "Output directory")
    private val outputDir: String = new SystemProperties().getOrElse("cwd", "")

    override def printUsage() {
      parser.printUsage(System.out)
      println("download <log-group-name> <start-time> <end-time>")
    }

    override def executeImpl(): Unit = {

      val logsClient: AWSLogsAsyncClient = {
        val client = new AWSLogsAsyncClient(Config.awsUserCredentials)
        client.setEndpoint("logs.eu-west-1.amazonaws.com")
        client
      }

      val dirName = s"awslogs.$logGroupName.$startTimeStr-to-$endTimeStr".replace(":", "-")

      def describeLogStreams(): Future[Seq[LogStream]] = {

        def describeLogStreams(request: DescribeLogStreamsRequest):
        Future[DescribeLogStreamsResult] = {
          AWSHelper.runAsyncCommand[DescribeLogStreamsRequest, DescribeLogStreamsResult](logsClient
            .describeLogStreamsAsync, request)
        }

        def getLogStreams(request: DescribeLogStreamsRequest,
                          acc: Seq[LogStream]): Future[Seq[LogStream]] = {
          describeLogStreams(request) flatMap { result =>
            val curr = result.getLogStreams filter { stream =>
              stream.getFirstEventTimestamp < endTime.getMillis &&
                stream.getLastEventTimestamp > startTime.getMillis
            }
            val maybeNextToken = Option(result.getNextToken)
            maybeNextToken map { nextToken =>
              getLogStreams(request.withNextToken(nextToken), acc ++ curr)
            } getOrElse {
              Future.successful(acc ++ curr)
            }
          }
        }

        val request = new DescribeLogStreamsRequest().withLogGroupName(logGroupName)
        getLogStreams(request, Nil)
      }

      def writeLogEvents(logStreamName: String): Future[Option[Path]] = {

        def getLogEventsResult(request: GetLogEventsRequest): Future[GetLogEventsResult] = {
          AWSHelper.runAsyncCommand[GetLogEventsRequest, GetLogEventsResult](logsClient
            .getLogEventsAsync, request)
        }

        def path(parts: String*): Path = Paths.get(outputDir, parts: _*)

        def initLogFile(fileName: String): Unit = {
          Files.createDirectories(path(dirName))
          val filePath = path(dirName, fileName)
          Files.deleteIfExists(filePath)
          Files.createFile(filePath)
        }

        def appendEvents(fileName: String, events: Seq[String]): Path = {
          val filePath = path(dirName, fileName)
          val contents = events.mkString("", "\n", "\n")
          Files.write(filePath,
            contents.getBytes(StandardCharsets.UTF_8),
            StandardOpenOption.APPEND)
        }

        def writeLogEvents(request: GetLogEventsRequest): Future[Option[Path]] = {
          getLogEventsResult(request) flatMap { result =>
            val events = result.getEvents
            if (events.nonEmpty) {
              val path = Some(appendEvents(logStreamName, events.map(_.getMessage)))
              Option(result.getNextForwardToken) map { nextToken =>
                writeLogEvents(request.withNextToken(nextToken))
              } getOrElse {
                Future.successful(path)
              }
            } else {
              Future.successful(None)
            }
          }
        }

        val request = new GetLogEventsRequest()
          .withLogGroupName(logGroupName)
          .withLogStreamName(logStreamName)
          .withStartTime(startTime.getMillis)
          .withEndTime(endTime.getMillis)
          .withStartFromHead(true)

        initLogFile(logStreamName)
        writeLogEvents(request)
      }

      println(s"Downloading $logGroupName logs from $startTime to $endTime")

      val eventualLogStreams = describeLogStreams()
      eventualLogStreams onSuccess {
        case logStreams => println(s"Found ${logStreams.size} relevant log streams")
      }
      eventualLogStreams onFailure {
        case NonFatal(e) => println(s"Finding log streams failed: ${e.getMessage}")
      }

      val writing = eventualLogStreams flatMap { logStreams =>
        val eventualPaths = logStreams map { stream =>
          val streamName = stream.getLogStreamName
          val eventualPath = writeLogEvents(streamName)
          eventualPath onSuccess {
            case path => println(s"Finished writing log of $streamName")
          }
          eventualPath onFailure {
            case NonFatal(e) => println(s"Downloading log events failed: ${e.getMessage}")
          }
          eventualPath
        }
        Future.sequence(eventualPaths)
      }

      writing onComplete { _ =>
        logsClient.shutdown()
        println(s"Downloaded logs are in $dirName")
      }
    }
  }

}
