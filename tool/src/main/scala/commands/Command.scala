package goo

import com.amazonaws.regions.{Region => AmazonRegion, Regions}
import org.kohsuke.args4j.{OptionDef, CmdLineParser, Argument, Option}
import org.kohsuke.args4j.spi.{SubCommand, SubCommandHandler, Setter}
import scala.util.control.Exception._

trait Command {

  @Option(name = "-h", aliases = Array("--help"), usage = "print help")
  protected val doPrintUsage: Boolean = false

  def printUsage() {
    parser.printUsage(System.out)
    println()
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

  @Option(name = "--test", metaVar = "TEST stage", usage = "deploy to TEST stage")
  private val test: Boolean = false

  protected def getStage = {
    (code, prod, test) match {
      case (true, false, false) => Some("CODE")
      case (false, true, false) => Some("PROD")
      case (false, false, true) => Some("TEST")
      case _ =>
        println("Invalid stage: Specify one of --code, --test or --prod.")
        None
    }
  }
}

trait Region {

  @Option(name = "--eu", metaVar = "EU region", usage = "specifies AWS EU region")
  private val eu: Boolean = false

  @Option(name = "--us", metaVar = "US region", usage = "specifies AWS US region")
  private val us: Boolean = false

  protected val defaultRegion = AmazonRegion.getRegion(Regions.fromName(Regions.EU_WEST_1.getName))

  protected def getRegion() = {
    (eu, us) match {
      case (true, false) => Some(defaultRegion)
      case (false, true) => Some(AmazonRegion.getRegion(Regions.fromName(Regions.US_EAST_1.getName)))
      case _ =>
        println("Invalid region: Specify one of --eu or --us.")
        None
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

    parser
  }
}