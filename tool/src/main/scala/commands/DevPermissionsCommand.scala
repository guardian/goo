package commands.aws

import commands.refreshAwsTokens.AwsIam
import goo.Command
import org.kohsuke.args4j.Argument

class DevPermissionsCommand extends Command {
  @Argument(multiValued = false, metaVar = "permission operation (grant|revoke|list)", required = true, index = 0)
  private val operation: String = ""

  @Argument(multiValued = false, metaVar = "developer email", required = false, index = 1)
  private val email: String = ""

  override def executeImpl() {
    val validEmail: Boolean = """([\w\.]+)@([\w\.]+)""".r.unapplySeq(email).isDefined
    operation match {
      case "grant" if validEmail => AwsIam.grantUserAccessToFederatedRole(email)
      case "revoke" if validEmail => AwsIam.revokeUserAccessToFederatedRole(email)
      case "list" => AwsIam.listEmails.map(println)
      case _ =>
        if(email.nonEmpty && !validEmail) println(s"Cancelled. Invalid email: $email")
        else println("Cancelled. Allowed permission operations: (grant|revoke|list)")
    }
  }
}