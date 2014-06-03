package goo.cloudformation

import org.kohsuke.args4j.Argument
import org.kohsuke.args4j.spi.{SubCommand, SubCommands}
import java.io.File
import com.amazonaws.services.s3.AmazonS3Client
import scala.util.control.Exception.allCatch
import collection.JavaConversions._

import goo.{Config, Command, Stage, StackName, FogAWSCredentials, AWSConfig, GooSubCommandHandler}
import com.amazonaws.services.s3.model.PutObjectResult
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient
import com.amazonaws.services.cloudformation.model.{Stack, Parameter, CreateStackRequest, UpdateStackRequest, DeleteStackRequest}

class CloudFormationCommand() extends Command {

  @Argument(handler = classOf[GooSubCommandHandler])
  @SubCommands(value = Array(
    new SubCommand(name = "update", impl = classOf[UpdateCommand]),
    new SubCommand(name = "up", impl = classOf[UpCommand]),
    new SubCommand(name = "destroy", impl = classOf[DestroyCommand])))
  private val cmd: Command = null;

  override def executeImpl() {
    if (cmd != null) {
      cmd.execute()
    } else {
      println("Invalid cloudformation command")
    }
  }
}

object CloudFormation {
  lazy val s3Client: Option[AmazonS3Client] = {
    Config.awsUserCredentials.map { provider =>
      new AmazonS3Client(provider)
    }
  }

  lazy val cloudFormationClient: Option[AmazonCloudFormationClient] = {
    Config.awsUserCredentials.map { provider =>
      val client = new AmazonCloudFormationClient(provider)
      client.setEndpoint("cloudformation.eu-west-1.amazonaws.com")
      client
    }
  }

  def uploadTemplate(stage: String, client: AmazonS3Client, templateFilename: String): Either[Throwable, PutObjectResult] = {

    val objectKey = new File(stage, templateFilename).getPath
    val file = new File(Config.cloudformationHome, templateFilename)

    val result = allCatch either client.putObject("aws-cloudformation", objectKey, file)
    result match {
      case Right(x) => {
        println(s"Uploaded template from: $file")
      }
      case Left(e) => {
        println(s"Exception uploading template: ${e.getMessage}")
      }
    }
    result
  }

  def describeStacks(client: AmazonCloudFormationClient): Either[Throwable, List[Stack]] = {

    val result = allCatch either client.describeStacks()
    result match {
      case Right(x) => Right(x.getStacks.toList)
      case Left(e) => {
        println(s"Exception describing stacks: ${e.getMessage}")
        Left(e)
      }
    }
  }

  def getParameters(stage: String, stackName: String, awsConfig: AWSConfig, fogConfig: FogAWSCredentials): List[Parameter] = {

    val pairs = Config.getCloudFormationParameters(stage, stackName) ++
        List( ("KeyName", awsConfig.getKey),
              ("StorageSecretAccessKey", fogConfig.getAWSSecretKey),
              ("StorageAccessKeyId", fogConfig.getAWSAccessKeyId),
              ("Stage", stage))

    pairs.map( pair => {
      val param = new Parameter()
      param.setParameterKey(pair._1)
      param.setParameterValue(pair._2.toString)
      param
    }).toList
  }
}

class UpdateCommand() extends Command with Stage with StackName {

  override def executeImpl() {

    for {
      stage <- getStage()
      awsConfig <- Config.awsKeyName
      storeCredentials <- Config.frontendStoreCredentials
      s3client <- CloudFormation.s3Client
      client <- CloudFormation.cloudFormationClient
      result <- CloudFormation.uploadTemplate(stage, s3client, templateFilename).right
      stackShortName <- Some(s"${stackName}-${stage}")
      describeResult <- CloudFormation.describeStacks(client).right
      stack <- describeResult.find(_.getStackName == stackShortName)
    } {
      val objectKey = new File(stage, templateFilename).getPath

      val request = new UpdateStackRequest()
        .withTemplateURL(s"https://s3-eu-west-1.amazonaws.com/aws-cloudformation/${objectKey}")
        .withStackName(stackShortName)
        .withParameters(CloudFormation.getParameters(stage, stackName, awsConfig, storeCredentials))

      val result = allCatch either client.updateStack(request)
      result match {
        case Right(x) => {
          println("Update Stack Request sent successfully.")
        }
        case Left(e) => {
          println(s"Exception updating stack: ${e.getMessage}")
        }
      }

      client.shutdown()
      s3client.shutdown()
    }
  }
}

class UpCommand() extends Command with Stage with StackName {

  override def executeImpl() {

    for {
      stage <- getStage()
      awsConfig <- Config.awsKeyName
      storeCredentials <- Config.frontendStoreCredentials
      s3client <- CloudFormation.s3Client
      client <- CloudFormation.cloudFormationClient
      result <- CloudFormation.uploadTemplate(stage, s3client, templateFilename).right
      stackShortName <- Some(s"${stackName}-${stage}")
    } {

      val objectKey = new File(stage, templateFilename).getPath

      val request = new CreateStackRequest()
        .withTemplateURL(s"https://s3-eu-west-1.amazonaws.com/aws-cloudformation/${objectKey}")
        .withStackName(stackShortName)
        .withParameters(CloudFormation.getParameters(stage, stackName, awsConfig, storeCredentials))

      val result = allCatch either client.createStack(request)
      result match {
        case Right(x) => {
          println("Create Stack Request sent successfully.")
        }
        case Left(e) => {
          println(s"Exception creating stack: ${e.getMessage}")
        }
      }

      client.shutdown()
      s3client.shutdown()
    }
  }
}

class DestroyCommand() extends Command with Stage with StackName {

  override def executeImpl() {

    for {
      stage <- getStage()
      client <- CloudFormation.cloudFormationClient
      stackShortName <- Some(s"${stackName}-${stage}")
      describeResult <- CloudFormation.describeStacks(client).right
      stack <- describeResult.find(_.getStackName == stackShortName)
    } {

      val request = new DeleteStackRequest()
        .withStackName(stackShortName)

      val result = allCatch either client.deleteStack(request)
      result match {
        case Right(x) => {
          println("Delete Stack Request sent successfully.")
        }
        case Left(e) => {
          println(s"Exception deleting stack: ${e.getMessage}")
        }
      }

      client.shutdown()
    }
  }
}