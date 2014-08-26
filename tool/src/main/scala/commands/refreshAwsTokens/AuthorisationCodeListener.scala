package commands.refreshAwsTokens

import javax.servlet.ServletContext

import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.DefaultServlet
import org.eclipse.jetty.webapp.WebAppContext
import org.scalatra._
import org.scalatra.servlet.ScalatraListener

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._

object AuthorisationCodeListener {

  val promiseInstanceKey: String = "promisedResult"

  def authenticationCode: Promise[String] = {
    val server = new Server(Config.gOAuth.port)
    val promisedAuthorisationCode = Promise[String]()

    // spawn a web server on a new thread
    Future {
      val context = new WebAppContext()
      context setContextPath "/"
      context.setResourceBase("src/main/webapp")
      context.setInitParameter(ScalatraListener.LifeCycleKey, classOf[ScalatraBootstrap].getCanonicalName)
      context.addEventListener(new ScalatraListener)
      context.addServlet(classOf[DefaultServlet], "/")
      context.setAttribute(promiseInstanceKey, promisedAuthorisationCode)
      server.setHandler(context)
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

    promisedAuthorisationCode.future.onComplete { x =>
      server.stop()
      println("Authorisation code listener stopped")
    }

    promisedAuthorisationCode
  }
}

class ScalatraBootstrap extends LifeCycle {
  override def init(context: ServletContext) {
    context.mount(new ScalatraServlet {
      get(Config.gOAuth.callbackUri) {
        val code = params("code")
        Future {
          Thread.sleep(100)
          context.getAttribute(AuthorisationCodeListener.promiseInstanceKey) match {
            case p: Promise[String] => p.success(code)
            case _ => println("Missing promise in servlet context")
          }
        }

        <span>
          <p>Authorization code retrieved:
            {code}
          </p>
          <p>
            <b>You may close this window.</b>
          </p>
          <p>Thank you</p>
        </span>
      }
    }, "/*")
  }
}
