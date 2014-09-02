package commands.refreshAwsTokens

import java.io.File
import java.net.URLDecoder

import com.amazonaws.auth.AWSSessionCredentials
import com.amazonaws.auth.profile.internal.Profile
import com.amazonaws.auth.profile.{ProfileCredentialsProvider, ProfilesConfigFileWriter}
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient
import com.amazonaws.services.identitymanagement.model.{GetRoleRequest, UpdateAssumeRolePolicyRequest}
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient
import com.amazonaws.services.securitytoken.model.AssumeRoleWithWebIdentityRequest
import goo.Config
import play.api.libs.json.{JsValue, Json}

import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

case class GooSessionCredentials(accessKeyId: String, secretKey: String, sessionToken: String) extends AWSSessionCredentials {
  override def getSessionToken: String = sessionToken

  override def getAWSSecretKey: String = secretKey

  override def getAWSAccessKeyId: String = accessKeyId
}

object GooSessionCredentials {
  val emptyCredentials = new GooSessionCredentials("", "", "")
  val nextgenProfile = "nextgen"
  val adminProfile = "nextgen-admin"
}

object AwsSts {

  import commands.refreshAwsTokens.Logging._

  def assumeRole(jsonWebToken: String, userEmail: String): Option[GooSessionCredentials] = {
    val client = new AWSSecurityTokenServiceClient(GooSessionCredentials.emptyCredentials)
    val credentials: Option[GooSessionCredentials] = Try(client.assumeRoleWithWebIdentity(
      new AssumeRoleWithWebIdentityRequest()
        .withRoleArn(Config.Aws.roleArn)
        .withRoleSessionName(userEmail)
        .withWebIdentityToken(jsonWebToken))
    ) match {
      case Success(result) => Some(GooSessionCredentials(
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

  def storeCredentials(profileName: String, credentials: GooSessionCredentials) {
    ProfilesConfigFileWriter.modifyOrInsertProfiles(
      new File(Config.Aws.credentialsLocation),
      new Profile(profileName, credentials))
  }

}

object AwsIam {

  import commands.refreshAwsTokens.Logging._

  def listEmails: List[String] = getEmailsFromRolePolicy(getExistingPolicy)

  def grantUserAccessToFederatedRole(email: String) {
    val existingEmails = listEmails
    if (!existingEmails.contains(email))
      updateAssumeRolePolicyDocument(generateRolePolicyDocument(email :: existingEmails))
    else
      logger.info("Policy already contained the given email.")
  }

  def revokeUserAccessToFederatedRole(email: String) {
    val existingEmails = listEmails
    if (existingEmails.contains(email))
      updateAssumeRolePolicyDocument(generateRolePolicyDocument(existingEmails diff List(email)))
    else
      logger.info("Policy did not contain the given email.")
  }

  private def getExistingPolicy: JsValue = {
    val client = createClient
    val policy = client.getRole(
      new GetRoleRequest().withRoleName(Config.Aws.roleName))
      .getRole
      .getAssumeRolePolicyDocument
    client.shutdown()
    Json.parse(URLDecoder.decode(policy, "utf8"))
  }

  private def generateRolePolicyDocument(emails: List[String]): String = {
    def policyDocumentTpl(content: String) = s"""{"Version":"2012-10-17","Statement":[$content]}"""
    def emailPolicyTpl(email: String) = s"""{"Sid":"","Effect":"Allow","Principal":{"Federated":"accounts.google.com"},"Action":"sts:AssumeRoleWithWebIdentity","Condition":{"StringLike":{"accounts.google.com:email":"$email"},"StringEquals":{"accounts.google.com:aud":"${Config.gOAuth.clientId}"}}}"""
    val newPolicy: String = policyDocumentTpl(emails map emailPolicyTpl mkString ",")
    logger.debug(s"New policy:\n$newPolicy")
    newPolicy
  }

  private def createClient: AmazonIdentityManagementClient =
    new AmazonIdentityManagementClient(new ProfileCredentialsProvider(Config.Aws.credentialsLocation, GooSessionCredentials.adminProfile))

  private def getEmailsFromRolePolicy(policy: JsValue): List[String] =
    (policy \\ "accounts.google.com:email").flatMap(_.asOpt[String]).toList

  private def updateAssumeRolePolicyDocument(policy: String) = {
    val client = createClient
    logger.debug("Updating IAM policy")
    Try(client.updateAssumeRolePolicy(
      new UpdateAssumeRolePolicyRequest()
        .withRoleName(Config.Aws.roleName)
        .withPolicyDocument(policy))
    ) match {
      case Failure(e) =>
        logger.error(s"Error updating policy (check permissions)\n$policy", e)
      case _ =>
        logger.info("Policy successfully updated")
    }
    client.shutdown()
  }
}
