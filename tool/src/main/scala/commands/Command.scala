package goo

import org.kohsuke.args4j.{OptionDef, CmdLineParser, Argument, Option}
import org.kohsuke.args4j.spi.{SubCommand, SubCommandHandler, Setter}
import scala.util.control.Exception._

trait Command {

  @Option(name = "-h", aliases = Array("--help"), usage = "print help")
  protected val doPrintUsage: Boolean = false

  def printUsage() {
    parser.printUsage(System.out)
    println
  }

  def execute() = {
    if (doPrintUsage) {
      printUsage()
    } else {
      executeImpl()
    }
  }

  protected def executeImpl()

  // This is a var so it can be set from the java-based implementation of SubCommandHandler,
  // in GooSubCommandHandler.
  protected var parser: CmdLineParser = null

  def setParser(newParser: CmdLineParser) {
    parser = newParser
  }
}

trait Stage {

  @Option(name = "--code", metaVar = "CODE stage", usage = "deploy to CODE stage")
  private val code: Boolean = false

  @Option(name = "--prod", metaVar = "PROD stage", usage = "deploy to PROD stage")
  private val prod: Boolean = false

  protected def getStage() = {
    (code, prod) match {
      case (true, false) => Some("CODE")
      case (false, true) => Some("PROD")
      case _ => {
        println("Invalid stage: Specify one of --code or --prod.")
        None
      }
    }
  }
}

trait StackName {

  @Argument(multiValued = false, metaVar = "stack name", usage = "short stack name", required = true, index = 0)
  protected val stackName: String = ""

  protected lazy val templateFilename = stackName + ".json"
}


class GooSubCommandHandler(parser: CmdLineParser, option: OptionDef, setter: Setter[AnyRef])
  extends SubCommandHandler(parser: CmdLineParser, option: OptionDef, setter: Setter[AnyRef]) {

  override protected def configureParser(subCmd: AnyRef, c: SubCommand): CmdLineParser = {
    val parser = new CmdLineParser(subCmd)

    allCatch opt {
      val command = subCmd.asInstanceOf[Command]
      command.setParser(parser)
    }

    return parser
  }
}