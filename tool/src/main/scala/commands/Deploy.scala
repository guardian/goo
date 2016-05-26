package goo.deploy

import commands.S3
import dispatch._
import goo.{Command, Config, GooSubCommandHandler, Stage}
import org.joda.time.DateTime
import org.kohsuke.args4j.spi.{SubCommand, SubCommands}
import org.kohsuke.args4j.{Argument, Option => option}
import play.api.libs.json._

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.Try

class DeployCommand() extends Command with Stage {

  @option(name = "-n", aliases = Array("--name"), metaVar = "names", usage = "specifies the projects to deploy")
  private val names: String = DeployCommand.defaultDeployProjectNames.mkString(",")

  @option(name = "-b", aliases = Array("--build"), metaVar = "buildIdString", usage = "specifies the build to deploy", required = true)
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
          case projectStatus if projectStatus.isRight => projectStatus.right.get
        } map (_.build.toInt)

        if (currBuildNumbers.isEmpty) {
          Some(true)
        } else {
          val currBuildNumber = currBuildNumbers.min
          if (buildId.toInt < currBuildNumber) {
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
    case class Build(description: String, statusUrl: String)

    // NOTE: you have to enable the status widget in Teamcity for any build you add here
    val buildsWeCareAbout = Seq(
      Build("Most recent build on master: ", "http://teamcity.gu-web.net:8111/externalStatus.html?buildTypeId=dotcom_master")
    )

    println(s"\n${Console.BLUE}Build status:\n")

    for (build <- buildsWeCareAbout) {

      // even though there are API endpoints we can use, I have gone with simply comparing the HTML widget
      // this way we avoid developers having to configure or share Teamcity credentials
      val request = url(build.statusUrl)

      Http(request).either() match {
        case Right(response) if response.getStatusCode == 200 =>
          if (response.getResponseBody.contains("success.png")) printStatus("SUCCESS") else printStatus("FAILED")
        case Right(response) =>
          println(f"${Console.WHITE}${build.description}%-25s${Console.RED}Unable to fetch status: ${response}")
        case Left(a) => println(f"${Console.WHITE}${build.description}%-25s${Console.RED}Unable to fetch status")
      }

      def printStatus(status: String) = status match {
        case "SUCCESS" => println(f"${Console.WHITE}${build.description}%-25s${Console.GREEN}SUCCESS")
        case other => println(f"${Console.WHITE}${build.description}%-25s${Console.RED}${blink(other)}")
      }
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
        .addHeader("Content-Type", "application/json")

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
    "image",
    "onward",
    "png-resizer",
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
