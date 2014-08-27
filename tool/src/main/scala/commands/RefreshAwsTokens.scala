package commands

import goo.Command

class RefreshAwsTokens() extends Command {

  override def executeImpl() {
    println("Refreshing tokens")
  }

}
