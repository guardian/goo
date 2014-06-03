package goo

import org.kohsuke.args4j.{Argument, CmdLineParser, Option}
import org.kohsuke.args4j.spi.{SubCommand, SubCommands}

import scala.collection.JavaConversions._
import scala.util.control.Exception.allCatch

import goo.groups.GroupsCommand
import goo.deploy.DeployCommand
import goo.cloudformation.CloudFormationCommand
import goo.ec2.Ec2Command

object GooCommand {

  def run(args: Array[String]) {

    val gooCommand = new GooCommand()

    allCatch either gooCommand.parser.parseArgument(args.toList) match {
      case Right(x) => {
        if (gooCommand.doPrintUsage || gooCommand.cmd == null){
          println("Goo subcommands:")
          println("\t groups (list|update)")
          println("\t cloudformation (up|update|destroy)")
          println("\t deploy (list)")
          println("\t ec2 list")
        } else {
          gooCommand.cmd.execute();
        }
      }
      case Left(e) => {
        println(e.getMessage)
        gooCommand.parser.printSingleLineUsage(System.out)
      }
    }
  }
}

class GooCommand() {

  @Option(name = "-h", aliases = Array("--help"), usage = "print help")
  private val doPrintUsage: Boolean = false

  val parser = new CmdLineParser(this)

  @Argument(handler = classOf[GooSubCommandHandler])
  @SubCommands(value = Array(
    new SubCommand(name = "groups", impl = classOf[GroupsCommand]),
    new SubCommand(name = "cloudformation", impl = classOf[CloudFormationCommand]),
    new SubCommand(name = "deploy", impl = classOf[DeployCommand]),
    new SubCommand(name = "ec2", impl = classOf[Ec2Command])))
  private val cmd: Command = null;
}
