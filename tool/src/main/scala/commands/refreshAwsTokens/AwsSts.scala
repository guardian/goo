package commands.refreshAwsTokens

import com.amazonaws.auth.AWSSessionCredentials
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient
import com.amazonaws.services.identitymanagement.model.{GetRoleRequest, UpdateAssumeRolePolicyRequest}
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient
import com.amazonaws.services.securitytoken.model.AssumeRoleWithWebIdentityRequest
import goo.Config
import play.api.libs.json.{JsValue, Json}
import scala.io.Source
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}
import java.io.{FileOutputStream, File}
import java.net.URLDecoder

case class AWSCredentials(accessKeyId: String, secretKey: String, sessionToken: String) extends AWSSessionCredentials {
  override def getSessionToken: String = sessionToken

  override def getAWSSecretKey: String = secretKey

  override def getAWSAccessKeyId: String = accessKeyId
}

object AwsSts {

  import Logging._

  def assumeRole(jsonWebToken: String, userEmail: String): Option[AWSCredentials] = {
    val client = new AWSSecurityTokenServiceClient(AWSCredentials("", "", ""))
    val credentials: Option[AWSCredentials] = Try(client.assumeRoleWithWebIdentity(
      new AssumeRoleWithWebIdentityRequest()
        .withRoleArn(Config.Aws.roleArn)
        .withRoleSessionName(userEmail)
        .withWebIdentityToken(jsonWebToken))
    ) match {
      case Success(result) => Some(AWSCredentials(
        result.getCredentials.getAccessKeyId,
        result.getCredentials.getSecretAccessKey,
        result.getCredentials.getSessionToken))
      case Failure(e) =>
        logger.error("Error assuming role", e)
        None
    }
    client.shutdown()
    credentials
  }

  def storeCredentials(ac: AWSCredentials) {
    val fileTpl: String = s"[nextgen]\n" +
      s"aws_access_key_id=${ac.accessKeyId}\n" +
      s"aws_secret_access_key=${ac.secretKey}\n" +
      s"aws_session_token=${ac.sessionToken}\n" +
      s"region=eu-west-1"
    // todo refactor this to replace only the nextgen properties, not the whole file

    Try(new FileOutputStream(Config.Aws.credentialsLocation).write(fileTpl.getBytes)).recover {
      case e => logger.error(s"Could not write to ${Config.Aws.credentialsLocation}", e)
    }
  }
}

object AwsIam {

  import Logging._

  type Email = String

  def listEmails: List[Email] = {
    getExistingPolicy.fold(List[Email]())(getEmailsFromRolePolicy)
  }

  def grantUserAccessToFederatedRole(email: Email) {
    val existingEmails = listEmails
    if (!existingEmails.contains(email))
      updateAssumeRolePolicyDocument(generateRolePolicyDocument(existingEmails union List(email)))
    else
      logger.info("Policy already contained the given email.")
  }

  def revokeUserAccessToFederatedRole(email: Email) {
    val existingEmails = listEmails
    if (existingEmails.contains(email))
      updateAssumeRolePolicyDocument(generateRolePolicyDocument(existingEmails diff List(email)))
    else
      logger.info("Policy did not contain the given email.")
  }

  private def getExistingPolicy: Option[JsValue] = createClient.map {
    client =>
      val policy = client.getRole(
        new GetRoleRequest().withRoleName(Config.Aws.roleName))
        .getRole
        .getAssumeRolePolicyDocument
      client.shutdown()
      policy
  }.map(URLDecoder.decode(_, "utf8"))
    .map(Json.parse)

  private def generateRolePolicyDocument(emails: List[Email]): String = {
    def policyDocumentTpl(content: String) = s"""{"Version":"2012-10-17","Statement":[$content]}"""
    def emailPolicyTpl(email: Email) = s"""{"Sid":"","Effect":"Allow","Principal":{"Federated":"accounts.google.com"},"Action":"sts:AssumeRoleWithWebIdentity","Condition":{"StringLike":{"accounts.google.com:email":"$email"},"StringEquals":{"accounts.google.com:aud":"${Config.gOAuth.clientId}"}}}"""
    val newPolicy: String = policyDocumentTpl(emails map emailPolicyTpl mkString ",")
    logger.debug(s"New policy:\n$newPolicy")
    newPolicy
  }

  private def createClient: Option[AmazonIdentityManagementClient] = AWSLocalStore.readCredentials.map(new AmazonIdentityManagementClient(_))

  private def getEmailsFromRolePolicy(policy: JsValue): List[Email] =
    (policy \\ "accounts.google.com:email").flatMap(_.asOpt[Email]).toList

  private def updateAssumeRolePolicyDocument(policy: String) = createClient.map {
    client =>
      logger.debug("Updating IAM policy")
      Try(client.updateAssumeRolePolicy(
        new UpdateAssumeRolePolicyRequest()
          .withRoleName(Config.Aws.roleName)
          .withPolicyDocument(policy))
      ) match {
        case Failure(e) =>
          logger.error(s"Error updating policy\n$policy", e)
        case _ =>
          logger.info("Policy successfully updated")
      }
      client.shutdown()
  }
}

object AWSLocalStore {

  import Logging._

  private val file = new File(Config.Aws.credentialsLocation)

  def readCredentials: Option[AWSCredentials] = getProps flatMap {
    tokens =>
      logger.debug(tokens.get("aws_access_key_id").mkString("\n"))

      for (
        accessKeyId <- tokens.get("aws_access_key_id");
        secretKey <- tokens.get("aws_secret_access_key");
        sessionToken <- tokens.get("aws_session_token")
      ) yield AWSCredentials(accessKeyId, secretKey, sessionToken)
  }

  def getProps: Option[Map[String, String]] = {
    val content: String = if (file.exists()) Source.fromFile(file).mkString else ""
    if (content.isEmpty) None
    else Some(Map() ++ Source.fromFile(file)
      .getLines()
      .filter(_.contains("="))
      .map {
      x => val y = x.split("=")
        (y(0), y(1))
    })
  }
}
