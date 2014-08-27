package commands

import goo.Command
import commands.refreshAwsTokens.{Logging, GOAuthWebServer, AwsSts}

class RefreshAwsTokens() extends Command {

  import Logging._

  override def executeImpl() {
    logger.info("Refreshing AWS tokens")
    for (
      gc <- GOAuthWebServer.retrieveCredentials;
      ac <- AwsSts.assumeRole(gc.jsonWebToken, gc.userEmail)
    ) yield {
      logger.debug(s"Updating AWS Credentials with $ac")
      AwsSts.storeCredentials(ac)
    }
  }

}
