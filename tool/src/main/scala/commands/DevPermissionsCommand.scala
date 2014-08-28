package commands

import goo.Command
import org.kohsuke.args4j.Argument
import commands.refreshAwsTokens.AwsIam

class DevPermissionsCommand extends Command {
  @Argument(multiValued = false, metaVar = "permission operation (grant|revoke)", usage = "(grant|revoke)", required = true, index = 0)
  private val operation: String = ""

  @Argument(multiValued = false, metaVar = "developer email", usage = "Guardian email address", required = false, index = 1)
  private val email: String = ""

  override def executeImpl() {
    val validEmail: Boolean = """([\w\.]+)@([\w\.]+)""".r.unapplySeq(email).isDefined
    if (operation == "list" || validEmail)
      operation match {
        case "grant" => AwsIam.grantUserAccessToFederatedRole(email)
        case "revoke" => AwsIam.revokeUserAccessToFederatedRole(email)
        case "list" => AwsIam.listEmails.map(println)
        case _ => println("Canceled. Allowed permission operations: (grant|revoke)")
      }
    else println(s"Canceled. Invalid email: $email")
  }
}