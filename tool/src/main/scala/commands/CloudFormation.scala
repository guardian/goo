package goo.cloudformation

import java.io.File

import com.amazonaws.regions.{ServiceAbbreviations, Region => AmazonRegion}
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient
import com.amazonaws.services.cloudformation.model._
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.PutObjectResult
import goo.{Command, Config, GooSubCommandHandler, StackName, Stage, Region}
import org.kohsuke.args4j.Argument
import org.kohsuke.args4j.spi.{SubCommand, SubCommands}

import scala.collection.JavaConversions._
import scala.util.control.Exception.allCatch

class CloudFormationCommand() extends Command {

  @Argument(handler = classOf[GooSubCommandHandler])
  @SubCommands(value = Array(
    new SubCommand(name = "update", impl = classOf[UpdateCommand]),
    new SubCommand(name = "up", impl = classOf[UpCommand]),
    new SubCommand(name = "destroy", impl = classOf[DestroyCommand])))
  private val cmd: Command = null

  override def executeImpl() {
    if (cmd != null) {
      cmd.execute()
    } else {
      println("Invalid cloudformation command")
    }
  }
}

object CloudFormation {
  lazy val s3Client: AmazonS3Client = new AmazonS3Client(Config.awsUserCredentials)

  def cloudFormationClient(region: AmazonRegion): AmazonCloudFormationClient = {
    val endpoint = region.getServiceEndpoint(ServiceAbbreviations.CloudFormation)
    val client = new AmazonCloudFormationClient(Config.awsUserCredentials)
    client.setEndpoint(endpoint)
    client
  }

  def uploadTemplate(stage: String, client: AmazonS3Client, templateFilename: String): Either[Throwable, PutObjectResult] = {

    val objectKey = new File(stage, templateFilename).getPath
    val file = new File(Config.cloudformationHome, templateFilename)

    val result = allCatch either client.putObject("aws-cloudformation", objectKey, file)
    result match {
      case Right(x) => println(s"Uploaded template from: $file")
      case Left(e) => println(s"Exception uploading template: ${e.getMessage}")
    }
    result
  }

  def describeStacks(client: AmazonCloudFormationClient): Either[Throwable, List[Stack]] = {

    val result = allCatch either client.describeStacks()
    result match {
      case Right(x) => Right(x.getStacks.toList)
      case Left(e) =>
        println(s"Exception describing stacks: ${e.getMessage}")
        Left(e)
    }
  }

  def getParameters(stage: String): List[Parameter] = {
    val param = new Parameter()
    param.setParameterKey("Stage")
    param.setParameterValue(stage)
    param :: Nil
  }
}

class UpdateCommand() extends Command with Stage with StackName with Region {

  override def executeImpl() {

    val client = CloudFormation.cloudFormationClient(getRegion().getOrElse(defaultRegion))

    for {
      stage <- getStage
      result <- CloudFormation.uploadTemplate(stage, CloudFormation.s3Client, templateFilename).right
      stackShortName <- Some(s"${stackName}-${stage}")
      describeResult <- CloudFormation.describeStacks(client).right
      stack <- describeResult.find(_.getStackName == stackShortName)
    } {
      val objectKey = new File(stage, templateFilename).getPath

      val request = new UpdateStackRequest()
        .withTemplateURL(s"https://s3-eu-west-1.amazonaws.com/aws-cloudformation/${objectKey}")
        .withStackName(stackShortName)
        .withCapabilities(Capability.CAPABILITY_IAM)
        .withParameters(CloudFormation.getParameters(stage))

      val result = allCatch either client.updateStack(request)
      result match {
        case Right(x) => println("Update Stack Request sent successfully.")
        case Left(e) => println(s"Exception updating stack: ${e.getMessage}")
      }

      CloudFormation.s3Client.shutdown()
    }

    client.shutdown()
  }
}

class UpCommand() extends Command with Stage with StackName with Region {

  override def executeImpl() {

    for {
      stage <- getStage
      region <- getRegion
      result <- CloudFormation.uploadTemplate(stage, CloudFormation.s3Client, templateFilename).right
      stackShortName <- Some(s"${stackName}-${stage}")
    } {

      val client = CloudFormation.cloudFormationClient(region)
      val objectKey = new File(stage, templateFilename).getPath

      val request = new CreateStackRequest()
        .withTemplateURL(s"https://s3-eu-west-1.amazonaws.com/aws-cloudformation/${objectKey}")
        .withStackName(stackShortName)
        .withCapabilities(Capability.CAPABILITY_IAM)
        .withParameters(CloudFormation.getParameters(stage))

      val result = allCatch either client.createStack(request)
      result match {
        case Right(x) => println("Create Stack Request sent successfully.")
        case Left(e) => println(s"Exception creating stack: ${e.getMessage}")
      }

      client.shutdown()
      CloudFormation.s3Client.shutdown()
    }
  }
}

class DestroyCommand() extends Command with Stage with StackName with Region {

  override def executeImpl() {

    val client = CloudFormation.cloudFormationClient(defaultRegion)

    for {
      stage <- getStage
      stackShortName <- Some(s"${stackName}-${stage}")
      describeResult <- CloudFormation.describeStacks(client).right
      stack <- describeResult.find(_.getStackName == stackShortName)
    } {

      if (stage == "PROD") {
        println("Can not delete PROD stacks")
      } else {
        val request = new DeleteStackRequest()
          .withStackName(stackShortName)

        val result = allCatch either client.deleteStack(request)
        result match {
          case Right(x) => println("Delete Stack Request sent successfully.")
          case Left(e) => println(s"Exception deleting stack: ${e.getMessage}")
        }
      }
    }
    client.shutdown()
  }
}