package com.gu.newsletterlistcleanse.braze

import io.circe.Decoder
import org.slf4j.{Logger, LoggerFactory}
import sttp.client._
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend

import scala.concurrent.duration._
import io.circe.parser.decode
import io.circe.syntax._
import org.asynchttpclient.{AsyncHttpClient, DefaultAsyncHttpClientConfig}
import org.asynchttpclient.Dsl.{asyncHttpClient, config}
import sttp.client.asynchttpclient.WebSocketHandler

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global


class BrazeClient {

  private val timeout = 5000.seconds

  private val sttpOptions = SttpBackendOptions.connectionTimeout(timeout)
  private val adjustFunction: DefaultAsyncHttpClientConfig.Builder => DefaultAsyncHttpClientConfig.Builder =
    (defaultConfig) => defaultConfig.setMaxConnections(3)
  implicit val sttpBackend: SttpBackend[Future, Nothing, WebSocketHandler] = AsyncHttpClientFutureBackend
    .usingConfigBuilder(adjustFunction, sttpOptions)

  val logger: Logger = LoggerFactory.getLogger(this.getClass)
  val brazeEndpoint = "https://rest.fra-01.braze.eu"

  private def withClientLogging[A](info: => String)(block: => Future[Either[BrazeError,A]]): Future[Either[BrazeError, A]] = {
    val results = block

    results.foreach {
      case Right(successResult) =>
        logger.info(s"BrazeClient success: $info")
        logger.debug(s"BrazeClient result: $successResult")
      case Left(errorResult) => logger.error(s"BrazeClient failure: $info $errorResult")
    }

    results
  }

  private def parseValidateResponse[T: Decoder](response: Response[Either[String, String]]): Either[BrazeError, T] = {

    def parseResponse[T: Decoder](body: String, code: Int) = {
      decode[T](body) match {
        case Left(parseError) =>
          logger.error("failure to parse Braze response", parseError)
          Left(BrazeError(code, body))
        case Right(parsedBody) => Right(parsedBody)
      }
    }

    response.body match {
      case Left(requestError) =>
        Left(BrazeError(response.code.code, requestError))
      case Right(body) =>
        parseResponse[T](body, response.code.code)
    }
  }

  def updateUser(apiKey: String, requests: UserTrackRequest): Future[Either[BrazeError, SimpleBrazeResponse]] = {
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

      response.map(parseValidateResponse[SimpleBrazeResponse])
    }
  }

  def getInvalidUsers(apiKey: String, request: UserExportRequest): Future[Either[BrazeError, List[String]]] = {
    val jsonRequest = request.asJson.toString
    withClientLogging(s"getting invalid user IDs") {
      val response = basicRequest
        .post(uri"$brazeEndpoint/users/export/ids")
        .readTimeout(timeout)
        .header("Content-type", "application/json")
        .auth.bearer(apiKey)
        .body(jsonRequest)
        .send()


      response.map(parseValidateResponse[ExportIdBrazeResponse]).map(parsedResponse =>
        parsedResponse match {
          case Left(error) => Left(error)
          case Right(validResponse) => Right(validResponse.invalidUserIds)
        }
      )
    }

  }
}
