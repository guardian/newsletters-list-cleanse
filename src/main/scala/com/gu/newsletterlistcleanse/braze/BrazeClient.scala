package com.gu.newsletterlistcleanse.braze

import io.circe.Decoder
import org.slf4j.{Logger, LoggerFactory}
import sttp.client._
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend

import scala.concurrent.duration._
import io.circe.parser.decode
import io.circe.syntax._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global


object BrazeClient {

  private val timeout = 5000.seconds
  implicit val sttpBackend = AsyncHttpClientFutureBackend(
    options=SttpBackendOptions.connectionTimeout(timeout)
  )

  val logger: Logger = LoggerFactory.getLogger(this.getClass)
  val brazeEndpoint = "https://rest.fra-01.braze.eu"

  private def withClientLogging[A](info: => String)(block: => Future[Either[BrazeError,A]]): Future[Either[BrazeError, A]] = {
    val results = block

    for {
      result <- results
      } {
        result.foreach { successResult =>
          logger.info(s"BrazeClient success $info $successResult")
        }

        result.left.foreach { errorResult =>
          logger.error(s"BrazeClient failure $info $errorResult")
        }
      }

    results
  }

  private def parseValidateResponse[T: Decoder](response: Future[Response[Either[String, String]]]): Future[Either[BrazeError, T]] = {

    def parseResponse[T: Decoder](body: String, code: Int) = {
      decode[T](body) match {
        case Left(parseError) =>
          logger.error("failure to parse Braze response", parseError)
          Left(BrazeError(code, body))
        case Right(parsedBody) => Right(parsedBody)
      }
    }

    response.map(r => {
      r.body match {
        case Left(requestError) =>
          Left(BrazeError(r.code.code, requestError))
        case Right(body) =>
          parseResponse[T](body, r.code.code)
      }
    })
  }

  def updateUser(apiKey: String, requests: List[UserTrackRequest]): Future[Either[BrazeError, SimpleBrazeResponse]] = {
    val jsonRequest = requests.asJson.toString
    // TODO: Make logging more useful whilst maintaining data security
    withClientLogging(s"updating user subscriptions"){
      val response = basicRequest
        .post(uri"$brazeEndpoint/users/track")
        .auth.bearer(apiKey)
        .header("Content-type", "application/json")
        .readTimeout(timeout)
        .body(jsonRequest)
        .send()

      parseValidateResponse[SimpleBrazeResponse](response)
    }
  }

  def getInvalidUsers(apiKey: String, request: UserExportRequest): Future[Either[BrazeError, List[String]]] = {
    val jsonRequest = request.asJson.toString
    withClientLogging(s"Checking for invalid user IDs") {
      val response = basicRequest
        .post(uri"$brazeEndpoint/users/export/ids")
        .readTimeout(timeout)
        .header("Content-type", "application/json")
        .auth.bearer(apiKey)
        .body(jsonRequest)
        .send()


      parseValidateResponse[ExportIdBrazeResponse](response).map(response =>
        response match {
        case Left(error) => Left(error)
        case Right(validResponse) => Right(validResponse.invalidUserIds)
        }
      )
    }

  }
}
