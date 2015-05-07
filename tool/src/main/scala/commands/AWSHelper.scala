package commands

import java.util.concurrent.{Future => JavaFuture}

import com.amazonaws.AmazonWebServiceRequest
import com.amazonaws.handlers.AsyncHandler

import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}

object AWSHelper {
  def runAsyncCommand[A <: AmazonWebServiceRequest, B](command: (A, AsyncHandler[A, B]) =>
    JavaFuture[B], request: A): Future[B] = {
    val promise = Promise[B]()

    val handler = new AsyncHandler[A, B] {
      override def onSuccess(request: A, result: B): Unit = {
        promise.complete(Success(result))
      }
      override def onError(exception: Exception): Unit = {
        promise.complete(Failure(exception))
      }
    }

    command(request, handler)

    promise.future
  }
}
