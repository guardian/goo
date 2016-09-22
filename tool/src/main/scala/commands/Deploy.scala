package goo.deploy

import com.ning.http.client.Response
import commands.S3
import dispatch._
import goo.{Command, Config, GooSubCommandHandler, Stage}
import org.joda.time.DateTime
import org.kohsuke.args4j.spi.{SubCommand, SubCommands}
import org.kohsuke.args4j.{Argument, Option => option}
import play.api.libs.json._
import play.api.libs.functional.syntax._

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.Try

class DeployCommand() extends Command with Stage {

  @option(name = "-n", aliases = Array("--name"), metaVar = "names", usage = "specifies the projects to deploy")
  private val names: String = DeployCommand.defaultDeployProjectNames.mkString(",")

  @option(name = "-b", aliases = Array("--build"), metaVar = "buildIdString", usage = "specifies the build to deploy (optional)")
  private val buildIdString: String = ""
  private lazy val buildId = Try {
    Integer.parseUnsignedInt(buildIdString)
  }.toOption.map(_.toString).getOrElse("lastSuccessful")

  private def namesSpec = names.split(",")

  @Argument(handler = classOf[GooSubCommandHandler])
  @SubCommands(value = Array(
    new SubCommand(name = "list", impl = classOf[ListCommand]),
    new SubCommand(name = "block", impl = classOf[BlockDeployCommand]),
    new SubCommand(name = "unblock", impl = classOf[UnblockDeployCommand])
  ))
  private val cmd: Command = null

  override def executeImpl() {
    if (cmd != null) {
      cmd.execute()
    } else {
      deploy()
    }
  }

  private def deploy() {

    val blockMessage = S3.get("aws-frontend-devtools", "deploy.lock")

    blockMessage.foreach{ message =>
      println(s"\n\n\n${Console.RED} Deploy blocked: ${Console.BLUE}$message")
      println(s"${Console.BLUE} To clear run: ${Console.WHITE}deploy unblock\n\n")
    }

    if (blockMessage.isEmpty) {

      printBuildStatus()

      val stackStatus = getFrontendStackStatus()
      printFrontendStackStatus(stackStatus)

      def okToDeployAnOldBuild(stage: String): Option[Boolean] = {
        val stageStatus = stackStatus.getOrElse(stage, Nil)
        val currBuildNumbers = stageStatus collect {
          case Right(projectStatus) => projectStatus
        } map (_.build.toInt)

        if (currBuildNumbers.isEmpty) {
          Some(true)
        } else {
          val currBuildNumber = currBuildNumbers.min
          val deployBuildNumber = Try(buildId.toInt).getOrElse(currBuildNumber)
          if (deployBuildNumber < currBuildNumber) {
            promptForAction(
              s"You're deploying build $buildId when $stage is currently on build $currBuildNumber. " +
                "Are you sure you want to do this?"
            )
          } else Some(true)
        }
      }

      for {
        stage <- getStage
        _ <- promptForAction(s"Are you sure you want to Deploy build $buildId to $stage? (if you see ${Console.RED}RED${Console.WHITE} above you want to think carefully)")
        _ <- okToDeployAnOldBuild(stage)
        key <- Config.riffRaffKey
        project <- if (stage == "PROD") namesSpec.intersect(DeployCommand.allProjectNames) else namesSpec
        if stage == "PROD" || !DeployCommand.projectsExcludedFromCode.contains(project)
      } {
        val deploy = Seq(
          "project" -> JsString(s"dotcom:${project}"),
          "build" -> JsString(buildId),
          "stage" -> JsString(stage.toUpperCase))

        val request = url("https://riffraff.gutools.co.uk/api/deploy/request")
          .secure
          .POST
          .addQueryParameter("key", key)
          .addHeader("Content-Type", "application/json")
          .setBody(JsObject(deploy).toString)

        val response = Http(request).either()

        response match {
          case Right(resp) if resp.getStatusCode == 200 =>
            val logUrl = Json.parse(resp.getResponseBody) \ "response" \ "logURL"
            println(s"${Console.GREEN}Deploying ${project}${Console.WHITE} - ${logUrl.toString()}")
          case Right(resp) =>
            println(s"${Console.RED}${resp.getStatusCode} ${resp.getStatusText} Deploy failed for ${project}${Console.WHITE}")
          case Left(throwable) =>
            println(s"${Console.RED}${throwable.getMessage} Deploy exception for ${project}${Console.WHITE}")
        }
      }

      Http.shutdown()
    }
  }

  private def printBuildStatus(): Unit = {

    def printStatus(color: String, msg: String) = println(f"${Console.WHITE}${"Most recent build on master: "}%-25s${color}${msg}")
    def printSuccess(status: String) = printStatus(Console.GREEN, status)
    def printFailure(status: String) = printStatus(Console.RED, blink(status))

    // Response deserialization
    case class Build(buildNumber: Int, status: String) {
      def printStatus() = {
        val msg = s"${status} (${buildNumber})"
        if (status.contains("SUCCESS")) printSuccess(msg) else printFailure(msg)
      }

    }
    case class Builds(builds: Seq[Build])
    object BuildsJsonDeserializer extends (Response => Builds) {

      override def apply(r: Response): Builds = {
        (dispatch.as.String andThen (jsonString => parse(jsonString)))(r)
      }

      implicit val buildReader: Reads[Build] = (
        (JsPath \ "number").read[String].map(_.toInt) and
        (JsPath \ "status").read[String]
        )(Build.apply _)

      implicit val buildsResponseReader: Reads[Builds] = (__ \ "build").read[Seq[Build]].map{ builds => Builds(builds) }

      private def parse(jsonString: String) = {
        val jsValue = Json.parse(jsonString)
        jsValue.as[Builds]
      }

    }

    println(s"\n${Console.BLUE}Build status:\n")

    // NOTE: you have to enable the guest account in Teamcity Settings
    val teamcityUrl = "https://teamcity.gu-web.net/guestAuth/app/rest/builds?locator=buildType:(id:dotcom_master),count:1&fields=count,build(number,status)"
    val request = url(teamcityUrl) <:< Map("Accept" -> "application/json")
    Http(request OK BuildsJsonDeserializer).either() match {
      case Right(response) if !response.builds.isEmpty =>
        response.builds.head.printStatus()
      case Right(response) =>
        printFailure(s"No build in response '$response'")
      case Left(ex) =>
        printFailure(s"Unable to fetch status. '$ex'")
    }
  }

  private def blink(msg: String) = s"${Console.BLINK}${Console.BOLD}$msg${Console.RESET}"

  private def getFrontendStackStatus(): Map[String, Seq[Either[String, RiffRaffHistoryItem]]] = {

    implicit val readsHistoryItem = Json.reads[RiffRaffHistoryItem]

    def getProjectStatus(riffRaffKey: String,
                         stage: String,
                         project: String): Future[Either[String, RiffRaffHistoryItem]] = {

      val request = url("https://riffraff.gutools.co.uk/api/history")
        .secure
        .GET
        .addQueryParameter("key", riffRaffKey)
        .addQueryParameter("projectName", s"dotcom:$project$$")
        .addQueryParameter("stage", stage)
        .addQueryParameter("pageSize", "1")

      Http(request).either.map {
        case Right(resp) if resp.getStatusCode == 200 =>
          val results = Json.parse(resp.getResponseBody) \ "response" \ "results"
          val items = results.validate[Seq[RiffRaffHistoryItem]].asOpt.getOrElse(Nil)
          items.headOption.toRight(s"${Console.RED}Riff-raff no response for $project${Console.WHITE}")
        case Right(resp) =>
          Left(s"${Console.RED}${resp.getStatusCode} ${resp.getStatusText} Riff-raff status check failed for $project${Console.WHITE}")
        case Left(throwable) =>
          Left(s"${Console.RED}${throwable.getMessage} Riff-raff check exception for $project${Console.WHITE}")
      }
    }

    def getStageStatus(riffRaffKey: String, stage: String): Seq[Either[String, RiffRaffHistoryItem]] = {
      val projects = stage match {
        case "CODE" => DeployCommand.allCodeProjectNames
        case _ => DeployCommand.allProjectNames
      }
      val responses = for {
        project <- projects
      } yield {
        getProjectStatus(riffRaffKey, stage, project)
      }
      Await.result(Future.sequence(responses), 10.seconds)
    }

    Config.riffRaffKey map { riffRaffKey =>
      Seq("CODE", "PROD") map { stage =>
        stage -> getStageStatus(riffRaffKey, stage)
      }
    } map (_.toMap) getOrElse Map.empty
  }

  private def printFrontendStackStatus(stackStatus: Map[String, Seq[Either[String, RiffRaffHistoryItem]]]) {
    def printStageStatus(stage: String): Unit = {
      println(s"\n${Console.BLUE}$stage deploy status:\n")
      for {
        projectStatus <- stackStatus.getOrElse(stage, Nil)
      } yield {
        projectStatus match {
          case Left(errMessage) => println(errMessage)
          case Right(status) => printHistoryItem(status)
        }
      }
    }
    printStageStatus("CODE")
    printStageStatus("PROD")
  }

  private def printHistoryItem(item: RiffRaffHistoryItem) {
    val status = item.status match {
      case "Completed" => f"${Console.GREEN}Completed${Console.WHITE}"
      case "Running" => f"${Console.YELLOW}Running${Console.WHITE}"
      case "Failed" => f"${Console.RED}${blink("FAILED")}${Console.WHITE}"
      case "Not running" => f"${Console.MAGENTA}Waiting${Console.WHITE}"
      case unknown => unknown
    }

    println(f"${Console.WHITE}${item.projectName}%-25s ${status}%-25s ${item.deployer}%-20s")
  }

  private def promptForAction(message: String): Option[Boolean] = {
    print(s"\n$message (y/n) ")

    val userInput = io.Source.stdin.getLines.next
    println()
    userInput match {
      case "y" => Some(true)
      case _ => None
    }
  }
}

object DeployCommand {

  val allProjectNames = List(
    "admin",
    "applications",
    "archive",
    "article",
    "commercial",
    "diagnostics",
    "discussion",
    "facia",
    "facia-press",
    "identity",
    "onward",
    "preview",
    "router",
    "rss",
    "sport",
    "static",
    "training-preview",
    "admin-jobs"
  )

  val defaultDeployProjectNames = List(
    "admin",
    "applications",
    "archive",
    "article",
    "commercial",
    "diagnostics",
    "discussion",
    "facia",
    "facia-press",
    "identity",
    "onward",
    "preview",
    "sport",
    "static",
    "rss",
    "admin-jobs"
  )

  val projectsExcludedFromCode = List(
    "preview",
    "training-preview"
  )

  lazy val allCodeProjectNames = allProjectNames.filterNot(projectsExcludedFromCode.toSet)
}

class ListCommand() extends Command {

  override def executeImpl() {
    println(s"${Console.CYAN}Default Deploy List:${Console.WHITE}")
    for (project <- DeployCommand.defaultDeployProjectNames) {
      println(project)
    }
    println(s"${Console.CYAN}Optional Deploy List:${Console.WHITE}")
    for (project <- DeployCommand.allProjectNames.diff(DeployCommand.defaultDeployProjectNames)) {
      println(project)
    }

  }
}

class BlockDeployCommand() extends Command {

  override def executeImpl() {
    S3.put("aws-frontend-devtools", "deploy.lock", DateTime.now().toString)
    println(s"${Console.CYAN}Deploy blocked")
  }
}

class UnblockDeployCommand() extends Command {

  override def executeImpl() {
    S3.delete("aws-frontend-devtools", "deploy.lock")
    println(s"${Console.CYAN}Deploy unblocked")
  }
}

case class RiffRaffHistoryItem(time: String,
                               uuid: String,
                               projectName: String,
                               build: String,
                               stage: String,
                               deployer: String,
                               recipe: String,
                               status: String,
                               logURL: String)
