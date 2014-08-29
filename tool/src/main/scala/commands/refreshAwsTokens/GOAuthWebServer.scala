package commands.refreshAwsTokens

import java.io
import java.util.concurrent.TimeoutException
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.amazonaws.services.cloudfront.model.InvalidArgumentException
import dispatch._
import goo.Config
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.{ServletHandler, ServletHolder}
import play.api.libs.json.Json

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future, Promise}
import scala.io.Source
import scala.language.postfixOps
import scala.reflect.io.{Directory, File}
import scala.util.{Failure, Success, Try}

case class GoogleCredentials(jsonWebToken: String, // used for 3rd party service authorisation
                             userEmail: String)

object GOAuthWebServer {
  import commands.refreshAwsTokens.GAuthLocalStore._
  import commands.refreshAwsTokens.HttpUtils._
  import commands.refreshAwsTokens.Logging._

  private val secureEndpoint: Req = host("accounts.google.com").secure / "o" / "oauth2"

  def retrieveCredentials: Option[GoogleCredentials] = {
    for {
      jwt <- obtainJwtFromAuthorisationCode(obtainAuthorisationCode)
      userEmail <- getLocalAccessToken flatMap retrieveUserEmail
    } yield {
      logger.debug(s"Google credentials obtained ($jwt, $userEmail)")
      GoogleCredentials(jwt, userEmail)
    }
  }

  def obtainAuthorisationCode: String = {
    openBrowser((secureEndpoint / "auth").GET
      .addQueryParameter("response_type", "code")
      .addQueryParameter("client_id", Config.gOAuth.clientId)
      .addQueryParameter("redirect_uri", Config.gOAuth.redirectUrl)
      .addQueryParameter("scope", "https://www.googleapis.com/auth/userinfo.email")
      .addQueryParameter("access_type", "offline")
      .addQueryParameter("approval_prompt", "auto")
      .addQueryParameter("login_hint", "email")
      .toRequest.getUrl)

    val authorizationCode: String = Await.result(AuthorisationCodeListener.authenticationCode, Config.gOAuth.timeout)
    storeAuthorisationCode(authorizationCode)
    logger.debug(s"Authorization code obtained: $authorizationCode")
    authorizationCode
  }

  def obtainJwtFromAuthorisationCode(authorizationCode: String): Option[String] =
    waitForResponse((secureEndpoint.POST / "token")
      .addParameter("code", authorizationCode)
      .addParameter("redirect_uri", Config.gOAuth.redirectUrl) // not needed!
      .addParameter("client_id", Config.gOAuth.clientId)
      .addParameter("client_secret", Config.gOAuth.clientSecret)
      .addParameter("grant_type", "authorization_code")
    ) match {
      case Success(response) =>
        val json = Json.parse(response)
        (json \ "access_token").asOpt[String] map storeAccessToken
        // todo why is the refresh token not present here?
        (json \ "refresh_token").asOpt[String] map storeRefreshToken
        (json \ "id_token").asOpt[String]
      case Failure(e) =>
        logger.error("Communication error", e)
        None
    }

  def retrieveUserEmail(accessToken: String): Option[String] = {
    def tryToGetEmailAddress(accessToken: String): Try[String] = waitForResponse(
      url(s"https://www.googleapis.com/plus/v1/people/me?access_token=$accessToken"))

    def refreshAccessToken: Try[String] = getLocalRefreshToken.map {
      refreshToken =>
        waitForResponse(
          (secureEndpoint / "token").POST
            .addParameter("client_id", Config.gOAuth.clientId)
            .addParameter("client_secret", Config.gOAuth.clientSecret)
            .addParameter("refresh_token", refreshToken)
            .addParameter("grant_type", "refresh_token")
        ) flatMap {
          response =>
            (Json.parse(response) \ "access_token").asOpt[String] match {
              case Some(accessToken) =>
                logger.debug(s"New access token obtained $accessToken")
                storeAccessToken(accessToken)
                Success(accessToken)
              case _ =>
                logger.error("Access token not found")
                Failure(new Exception("Malformed response"))
            }
        }
    }.getOrElse(Failure(new InvalidArgumentException("Local access token not found")))

    val response: String = tryToGetEmailAddress(accessToken: String).recoverWith {
      case e =>
        logger.error("Failed to retrieve user email, retrying", e)
        refreshAccessToken.flatMap(tryToGetEmailAddress)
    }.recover {
      case e =>
        logger.error("Failed to retrieve user email, giving up", e)
        "{}"
    }.get

    for (email <- ((Json.parse(response) \ "emails")(0) \ "value").asOpt[String]) yield email
  }
}

object HttpUtils {
  val maxWaitPerResponse = 30.seconds

  def waitForResponse(req: Req): Try[String] = Try(Await.result(Http(req OK as.String), maxWaitPerResponse))

  def openBrowser(url: String) =
    java.awt.Desktop.getDesktop.browse(new java.net.URI(url))
}

object GAuthLocalStore {
  private val storagePath = Directory(System.getProperty("user.home")) / ".goauth" / "credentials"
  private val refreshTokenProp: String = "refreshToken"
  private val accessTokenProp: String = "accessToken"
  private val authorisationCodeProp: String = "authorisationCode"

  def getLocalRefreshToken: Option[String] = readProperty(refreshTokenProp)

  def getLocalAccessToken: Option[String] = readProperty(accessTokenProp)

  def getLocalAuthorisationCode: Option[String] = readProperty(authorisationCodeProp)

  def storeRefreshToken(token: String) = writeProperty(refreshTokenProp, token)

  def storeAccessToken(token: String) = writeProperty(accessTokenProp, token)

  def storeAuthorisationCode(code: String) = writeProperty(authorisationCodeProp, code)

  private def writeProperty(name: String, value: String) = {
    val file: File = storagePath.toFile
    if (!file.exists) {
      storagePath / "src/main" createDirectory()
      storagePath.createFile()
    }
    file.writeAll(Json.toJson(getTokens + (name -> value)).toString())
  }

  private def getTokens: Map[String, String] = {
    val file: io.File = storagePath.jfile
    val content: String = if (file.exists()) Source.fromFile(file).mkString else ""
    (if (content.isEmpty) None else Json.parse(content).asOpt[Map[String, String]])
      .getOrElse(Map())
  }

  private def readProperty(refreshTokenProp: String): Option[String] = getTokens.get(refreshTokenProp)
}

object AuthorisationCodeListener {

  import commands.refreshAwsTokens.Logging._

  val promiseInstanceKey: String = "promisedResult"

  def authenticationCode: Future[String] = {
    val promisedAuthorisationCode = Promise[String]()
    val server = new Server(Config.gOAuth.port)
    server.setHandler(new ServletHandler(){
      addServletWithMapping(new ServletHolder(new HttpServlet() {
        override protected def doGet(req: HttpServletRequest, resp: HttpServletResponse) {
          val code: String = req.getParameter("code")
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
          Future {
            // this will cause the server to stop, so deferring the completion to allow for response to be sent
            promisedAuthorisationCode.success(code)
          }
        }
      }), "/*")
    })
    server.start()
    logger.debug("server started")
    // spawn a web server on a new thread
    Future {
      server.join()
    }
    // wait for a certain amount of time for user to authenticate
    Future {
      Thread.sleep(Config.gOAuth.timeout.toMillis - 100)
      val msg = s"Authentication code was not received within ${Config.gOAuth.timeout.toSeconds} seconds"
      if (!promisedAuthorisationCode.isCompleted)
        promisedAuthorisationCode.failure(new TimeoutException(msg))
    }

    promisedAuthorisationCode.future.onComplete { _ =>
      server.stop()
      logger.debug("Authorisation code listener stopped")
    }

    promisedAuthorisationCode.future
  }
}