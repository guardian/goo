package commands.aws

import commands.refreshAwsTokens.{AwsSts, GOAuthWebServer, GooSessionCredentials}
import goo.Command

class RefreshTokensCommand() extends Command {

  import commands.refreshAwsTokens.Logging._

  override def executeImpl() {
    logger.info("Refreshing AWS tokens")
    for {
      googleCredentials <- GOAuthWebServer.retrieveCredentials
      awsCredentials <- AwsSts.assumeRole(googleCredentials.jsonWebToken, googleCredentials.userEmail)
    } yield {
      logger.debug(s"Updating AWS Credentials with $awsCredentials")
      AwsSts.storeCredentials(GooSessionCredentials.nextgenProfile, awsCredentials)
      logger.info("AWS credentials updated")
    }
  }
}
