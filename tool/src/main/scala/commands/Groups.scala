package goo.groups

import org.kohsuke.args4j.{Option => option, Argument}
import org.kohsuke.args4j.spi.{SubCommand, SubCommands}
import com.amazonaws.services.autoscaling.AmazonAutoScalingAsyncClient
import com.amazonaws.services.autoscaling.model.{UpdateAutoScalingGroupRequest, DescribeAutoScalingGroupsRequest}
import collection.JavaConversions._
import scala.util.control.Exception.allCatch

import goo.{Command, Config, GooSubCommandHandler}

class GroupsCommand() extends Command {

  override def executeImpl() {
    cmd.execute()
  }

  override def printUsage() {
    parser.printUsage(System.out)
    println("groups help here")
  }

  @Argument(handler = classOf[GooSubCommandHandler])
  @SubCommands(value = Array(
    new SubCommand(name = "list", impl = classOf[ListCommand]),
    new SubCommand(name = "update", impl = classOf[UpdateCommand])))
  private val cmd: Command = null
}

object GroupsCommand {
  lazy val autoscalingClient: Option[AmazonAutoScalingAsyncClient] = {
    Config.awsUserCredentials.map { provider =>
      val client = new AmazonAutoScalingAsyncClient(provider)
      client.setEndpoint("autoscaling.eu-west-1.amazonaws.com")
      client
    }
  }
}

class ListCommand() extends Command {

  @option(name = "--verbose", metaVar = "verbose", usage = "Verbose group listing")
  private val verbose: Boolean = false

  @Argument(multiValued = false, metaVar = "group name", usage = "Group name grep argument(optional)")
  private val groupFilter: String = ""
  def listEverything = groupFilter.isEmpty

  override def executeImpl() {

    GroupsCommand.autoscalingClient.map { client =>

      val request = new DescribeAutoScalingGroupsRequest()

      val blockedResult = client.describeAutoScalingGroups(request)

      val groups = blockedResult.getAutoScalingGroups.filter(_.getAutoScalingGroupName.toLowerCase.contains(groupFilter.toLowerCase))

      groups.map { group =>
        print(f"${group.getAutoScalingGroupName}%-64s")

        if (listEverything && !verbose) {
          print(s"${group.getMinSize}/${group.getDesiredCapacity}/${group.getMaxSize}\n")
        } else {

          println(s"\n\tLoad Balancers: [${group.getLoadBalancerNames.mkString(",")}]")
          println(s"\tMin/Desired/Max = ${group.getMinSize}/${group.getDesiredCapacity}/${group.getMaxSize}\n")

          group.getInstances.map( instance => {
            println(f"\t${instance.getInstanceId}%-10s ${instance.getHealthStatus}/${instance.getLifecycleState}")
          })

          println()
        }
      }

      client.shutdown()
    }
  }
}

class UpdateCommand() extends Command {

  @Argument(multiValued = false, metaVar = "group name", usage = "autoscaling group name", required = true, index = 0)
  private val groupName: String = ""

  @Argument(multiValued = false, metaVar = "min size", usage = "group min size", required = true, index = 1)
  private val minSize: Integer = 3

  @Argument(multiValued = false, metaVar = "desired capacity", usage = "group desired capacity", required = true, index = 2)
  private val desiredCapacity: Integer = 3

  @Argument(multiValued = false, metaVar = "max size", usage = "group max size", required = true, index = 3)
  private val maxSize: Integer = 12

  override def executeImpl() {

    for {
      client <- GroupsCommand.autoscalingClient
    } {
      val request = new UpdateAutoScalingGroupRequest()
        .withAutoScalingGroupName(groupName)
        .withMinSize(minSize)
        .withDesiredCapacity(desiredCapacity)
        .withMaxSize(maxSize)

      val result = allCatch either client.updateAutoScalingGroup(request)

      result match {
        case Right(x) => {
          println(s"Updated autoscaling group")
        }
        case Left(e) => {
          println(s"Exception updating autoscaling group: ${e.getMessage}")
        }
      }

      client.shutdown()
    }
  }
}