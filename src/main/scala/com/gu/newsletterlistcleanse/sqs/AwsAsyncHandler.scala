package com.gu.newsletterlistcleanse.sqs

import java.util.concurrent.{Future => JFuture}

import com.amazonaws.handlers.AsyncHandler
import com.amazonaws.AmazonWebServiceRequest
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.{Future, Promise}

// Taken from https://github.com/guardian/support-service-lambdas/blob/57e62ad5083b71c7ace0a5ba33ad42e9776f5689/lib/effects-sqs/src/main/scala/com/gu/effects/sqs/AWSAsyncHandler.scala

class AwsAsyncHandler[Request <: AmazonWebServiceRequest, Response](
      f: (Request, AsyncHandler[Request, Response]) => JFuture[Response], request: Request
      )
  extends AsyncHandler[Request, Response] {
  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  f(request, this)

  private val promise = Promise[Response]()

  override def onError(exception: Exception): Unit = {
    logger.warn("Failure from AWSAsyncHandler", exception)
    promise.failure(exception)

  }

  override def onSuccess(request: Request, result: Response): Unit = {
    logger.debug(s"Successful result from AWS AsyncHandler $result")
    promise.success(result)
  }

  def future: Future[Response] = promise.future
}

object AwsAsync {

  def apply[Request <: AmazonWebServiceRequest, Response](
      f: (Request, AsyncHandler[Request, Response]) =>
        JFuture[Response],
        request: Request
      ): Future[Response] = {
    val handler = new AwsAsyncHandler[Request, Response](f, request)
    handler.future
  }
}
