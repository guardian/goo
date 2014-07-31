package goo.deploy

import org.kohsuke.args4j.{Argument, Option}
import org.kohsuke.args4j.spi.{SubCommand, SubCommands}
import dispatch._
import scala.concurrent.ExecutionContext.Implicits.global

import goo.{Command, Stage, Config, Json, GooSubCommandHandler}

class DeployCommand() extends Command with Stage {

  @Option(name = "--name", metaVar = "names", usage = "specifies the projects to deploy")
  private val names: String = DeployCommand.projectNames.mkString(",")
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
    for {
      key <- Config.riffRaffKey
      stage <- getStage()
      project <- namesSpec.intersect(DeployCommand.projectNames)
    } {
      val deploy = Map(
        "project" -> s"frontend::${project}",
        "build"   -> "lastSuccessful",
        "stage"   -> stage.toUpperCase)

      val request = url("https://riffraff.gutools.co.uk/api/deploy/request")
        .secure
        .POST
        .addQueryParameter("key", key)
        .addHeader("Content-Type", "application/json")
        .setBody(Json.serialise(deploy))

      val response = Http(request).either()

      response match {
        case Right(resp) if resp.getStatusCode == 200 => {
          val responseObject = Json.deserialize[Map[String,Object]](resp.getResponseBody).getOrElse("response",Map.empty).asInstanceOf[Map[String,String]]
          println(s"${Console.GREEN}Deploying ${project}${Console.WHITE} - ${responseObject.getOrElse("logURL","")}")
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
}

object DeployCommand {

  val projectNames = List(
    "admin",
    "applications",
    "article",
    "commercial",
    "diagnostics",
    "discussion",
    "facia",
    "facia-tool",
    "identity",
    "onward",
    "preview",
    "sport",
    "archive"
  )
}

class ListCommand() extends Command {

  override def executeImpl() {
    for (project <- DeployCommand.projectNames) {
      println(project)
    }
  }
}