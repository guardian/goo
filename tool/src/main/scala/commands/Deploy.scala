package goo.deploy

import org.kohsuke.args4j.{Argument, Option => option}
import org.kohsuke.args4j.spi.{SubCommand, SubCommands}
import dispatch._
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.json._

import goo.{Command, Stage, Config, GooSubCommandHandler}

class DeployCommand() extends Command with Stage {

  @option(name = "-n", aliases = Array("--name"), metaVar = "names", usage = "specifies the projects to deploy")
  private val names: String = DeployCommand.defaultDeployProjectNames.mkString(",")

  private def namesSpec = names.split(",")

  @Argument(handler = classOf[GooSubCommandHandler])
  @SubCommands(value = Array(
    new SubCommand(name = "list", impl = classOf[ListCommand])))
  private val cmd: Command = null;

  override def executeImpl() {
    if (cmd != null) {
      cmd.execute()
    } else {
      deploy()
    }
  }

  private def deploy() {

    printFrontendStackStatus()

    for {
      response <- promptForAction("Are you sure you want to Deploy?")
      key <- Config.riffRaffKey
      stage <- getStage
      project <- if (stage == "PROD") namesSpec.intersect(DeployCommand.allProjectNames) else namesSpec
      if (stage == "PROD" || !DeployCommand.projectsExcludedFromCode.contains(project))
    } {
      val deploy = Seq(
        "project" -> JsString(s"frontend::${project}"),
        "build" -> JsString("lastSuccessful"),
        "stage" -> JsString(stage.toUpperCase))

      val request = url("https://riffraff.gutools.co.uk/api/deploy/request")
        .secure
        .POST
        .addQueryParameter("key", key)
        .addHeader("Content-Type", "application/json")
        .setBody(JsObject(deploy).toString)

      val response = Http(request).either()

      response match {
        case Right(resp) if resp.getStatusCode == 200 => {
          val logUrl = Json.parse(resp.getResponseBody) \ "response" \ "logURL"
          println(s"${Console.GREEN}Deploying ${project}${Console.WHITE} - ${logUrl.toString}")
        }
        case Right(resp) => {
          println(s"${Console.RED}${resp.getStatusCode} ${resp.getStatusText} Deploy failed for ${project}${Console.WHITE}")
        }
        case Left(throwable) => {
          println(s"${Console.RED}${throwable.getMessage} Deploy exception for ${project}${Console.WHITE}")
        }
      }
    }

    Http.shutdown()
  }

  private def printFrontendStackStatus() {

    implicit val readsHistoryItem = Json.reads[RiffRaffHistoryItem]

    for {
      key <- Config.riffRaffKey
      stage <- List("CODE", "PROD")
    } {

      println(s"\n$stage status:\n");

      val projects = stage match {
        case "CODE" => DeployCommand.allCodeProjectNames
        case _ => DeployCommand.allProjectNames
      }

      for {
        project <- projects
      } {
        val request = url("https://riffraff.gutools.co.uk/api/history")
          .secure
          .GET
          .addQueryParameter("key", key)
          .addQueryParameter("projectName", s"frontend::${project}")
          .addQueryParameter("stage", stage)
          .addQueryParameter("pageSize", "1")
          .addHeader("Content-Type", "application/json")

        val response = Http(request).either()

        response match {
          case Right(resp) if resp.getStatusCode == 200 => {

            val results = Json.parse(resp.getResponseBody) \ "response" \ "results"
            val items = results.validate[Seq[RiffRaffHistoryItem]].asOpt.getOrElse(Nil)

            items.map(printHistoryItem)
          }
          case Right(resp) => {
            println(s"${Console.RED}${resp.getStatusCode} ${resp.getStatusText} Riff-raff status check failed for ${project}${Console.WHITE}")
          }
          case Left(throwable) => {
            println(s"${Console.RED}${throwable.getMessage} Riff-raff check exception for ${project}${Console.WHITE}")
          }
        }
      }
    }
  }

  private def printHistoryItem(item: RiffRaffHistoryItem) {
    val status = item.status match {
      case "Completed" => f"${Console.GREEN}Completed${Console.WHITE}"
      case "Running" => f"${Console.YELLOW}Running${Console.WHITE}"
      case "Failed" => f"${Console.RED}Failed${Console.WHITE}"
      case "Not running" => f"${Console.MAGENTA}Waiting${Console.WHITE}"
      case unknown => unknown
    }

    println(f"${item.projectName}%-25s ${status}%-25s ${item.deployer}%-20s")
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
    "article",
    "commercial",
    "diagnostics",
    "discussion",
    "facia",
    "facia-tool",
    "facia-press",
    "identity",
    "onward",
    "preview",
    "sport",
    "archive"
  )

  val defaultDeployProjectNames = List(
    "admin",
    "applications",
    "article",
    "commercial",
    "diagnostics",
    "discussion",
    "facia",
    "identity",
    "onward",
    "preview",
    "sport",
    "archive"
  )

  val projectsExcludedFromCode = List(
    "preview"
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

case class RiffRaffHistoryItem(
                                time: String,
                                uuid: String,
                                projectName: String,
                                build: String,
                                stage: String,
                                deployer: String,
                                recipe: String,
                                status: String,
                                logURL: String)


