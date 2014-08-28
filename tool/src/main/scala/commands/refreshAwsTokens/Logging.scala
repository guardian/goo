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
    def warn(msg:String, e:Throwable) = if(Logging.warn) println(s"WARN:$msg\n${e.getCause}\n${e.getStackTrace}")
    def error(msg:String) = if(Logging.error) println(s"ERROR: $msg")
    def error(msg:String, e:Throwable) = if(Logging.error) println(s"ERROR:$msg\n${e.getMessage}\n${e.getStackTraceString}")
  }
}
