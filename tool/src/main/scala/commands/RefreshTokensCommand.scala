package commands

import goo.Command
import commands.refreshAwsTokens.{Logging, GOAuthWebServer, AwsSts}

class RefreshTokensCommand() extends Command {

  import Logging._

  override def executeImpl() {
    logger.info("Refreshing AWS tokens")
    for (
      gc <- GOAuthWebServer.retrieveCredentials;
      ac <- AwsSts.assumeRole(gc.jsonWebToken, gc.userEmail)
    ) yield {
      logger.debug(s"Updating AWS Credentials with $ac")
      logger.info("AWS credentials updated")
      AwsSts.storeCredentials(ac)
    }
  }

}
