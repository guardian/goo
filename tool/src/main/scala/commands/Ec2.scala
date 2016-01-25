package goo.ec2

import com.amazonaws.regions.{Regions, Region}
import org.kohsuke.args4j.Argument
import org.kohsuke.args4j.spi.{SubCommand, SubCommands}
import com.amazonaws.services.ec2.AmazonEC2Client
import com.amazonaws.services.ec2.model.Instance
import scala.util.control.Exception.allCatch
import collection.JavaConversions._

import goo.{Config, Command, GooSubCommandHandler}

class Ec2Command() extends Command {

  @Argument(handler = classOf[GooSubCommandHandler])
  @SubCommands(value = Array(new SubCommand(name = "list", impl = classOf[ListCommand])))
  private val cmd: Command = null

  override def executeImpl() {
    if (cmd != null) {
      cmd.execute()
    } else {
      println("Invalid ec2 command")
    }
  }
}

object Ec2 {
  lazy val ec2Client: AmazonEC2Client = {
    val client = new AmazonEC2Client(Config.awsUserCredentials)
    client.setRegion(Region.getRegion(Regions.EU_WEST_1))
    client
  }
}

case class Ec2Instance(
  stage: String,
  role: String,
  state: String,
  id: String,
  address: String
)

class ListCommand() extends Command {

  override def executeImpl() {

    val instances = getInstances(Ec2.ec2Client).map ( instance => {

      Ec2Instance(
        instance.getTags.find(_.getKey == "Stage").map(_.getValue).getOrElse("-"),
        instance.getTags.find(_.getKey == "Role").map(_.getValue).getOrElse("-"),
        instance.getState.getName,
        instance.getInstanceId,
        instance.getPublicDnsName)
    }).sortBy(_.stage)

    for (box <- instances) {

      val stage = box.stage match {
        case "CODE" => f"${Console.MAGENTA}${box.stage}${Console.WHITE}"
        case "PROD" => f"${Console.CYAN}${box.stage}${Console.WHITE}"
        case _ => box.stage
      }

      val state = box.state match {
        case "running" => f"${Console.GREEN}${box.state}${Console.WHITE}"
        case "terminated"|
             "stopped" => f"${Console.RED}${box.state}${Console.WHITE}"
        case "shutting-down" => f"${Console.YELLOW}${box.state}${Console.WHITE}"
        case _ => box.state
      }

      println(f"${stage}%-5s ${box.role}%-25s ${state}%-20s ${box.id}%-15s ${box.address}%-20s")
    }

    Ec2.ec2Client.shutdown()

  }

  private def getInstances(client: AmazonEC2Client): List[Instance] = {

    val result = allCatch either client.describeInstances().getReservations.flatMap(_.getInstances).toList

    result match {
      case Right(list) => list
      case Left(ex) =>
        println(s"Error: ${ex.getMessage}")
        Nil
    }
  }
}
