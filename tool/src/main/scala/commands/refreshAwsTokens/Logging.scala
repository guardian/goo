package commands.refreshAwsTokens

object Logging {

  val debug = false
  val info = true
  val warn = true
  val error = true

  object logger {
    def debug(msg:String) = if(Logging.debug) println(s"DEBUG: $msg")

    def info(msg:String) = if(Logging.info) println(s"INFO: $msg")

    def warn(msg:String) = if(Logging.warn) println(s"WARN: $msg")
    def warn(msg:String, e:Throwable) = if(Logging.warn) printWithThrowable("WARN", msg, e)

    def error(msg:String) = if(Logging.error) println(s"ERROR: $msg")
    def error(msg:String, e:Throwable) = if(Logging.error) printWithThrowable("ERROR", msg, e)
  }

  private def printWithThrowable(level:String, msg: String, e: Throwable) {
    println(s"$level: $msg\n${e.getMessage}" + (if (debug) "\n" + e.getStackTraceString else ""))
  }
}
