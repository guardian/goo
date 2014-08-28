package commands.refreshAwsTokens

import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.{ServletHolder, ServletHandler}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import javax.servlet.http.{HttpServletResponse, HttpServletRequest, HttpServlet}

object AuthorisationCodeListener {
  import commands.refreshAwsTokens.Logging._

  val promiseInstanceKey: String = "promisedResult"

  def authenticationCode: Promise[String] = {
    val server = new Server(Config.gOAuth.port)
    val promisedAuthorisationCode = Promise[String]()

    // spawn a web server on a new thread
    Future {
      val handler: ServletHandler = new ServletHandler()
      handler.addServletWithMapping(new ServletHolder(new HttpServlet() {
        override protected def doGet(req: HttpServletRequest, resp: HttpServletResponse) {
          val code: String = req.getParameter("code")
          Future {
            // this will cause the server to stop, so deferring the completion to allow for response to be sent
            promisedAuthorisationCode.success(code)
          }
          resp.setContentType("text/html")
          resp.getWriter.println(
            <span>
              <p>Authorization code retrieved:
                {code}
              </p>
              <p>
                <b>You may close this window.</b>
              </p>
              <p>Thank you</p>
            </span>.toString())
        }
      }), "/*")
      server.setHandler(handler)

      server.start()
      println("server started")
      server.join()
    }
    // wait for a certain amount of time for user to authenticate
    Future {
      Thread.sleep(Config.gOAuth.timeout.toMillis - 100)
      val msg = s"Authentication code was not received within ${Config.gOAuth.timeout.toSeconds} seconds"
      if (!promisedAuthorisationCode.isCompleted)
        promisedAuthorisationCode.failure(new TimeoutException(msg))
    }

    promisedAuthorisationCode.future.onComplete(_ => {
      server.stop()
      logger.debug("Authorisation code listener stopped")
    })

    promisedAuthorisationCode
  }
}