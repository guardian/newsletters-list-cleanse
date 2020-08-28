package com.gu.newsletterlistcleanse.braze

import org.slf4j.{Logger, LoggerFactory}
import scalaj.http._
import io.circe.parser.decode
import io.circe.syntax._

object BrazeClient {

  private val timeout = 5000

  val logger: Logger = LoggerFactory.getLogger(this.getClass)
  val brazeEndpoint = "https://rest.fra-01.braze.eu"

  private def withClientLogging[A](info: => String)(block: => Either[BrazeError,A]): Either[BrazeError, A] = {
    val result = block

    result.foreach { successResult =>
      logger.info(s"BrazeClient success $info $successResult")
    }

    result.left.foreach { errorResult =>
      logger.error(s"BrazeClient failure $info $errorResult")
    }

    result
  }

  private def parseValidateResponse(response: HttpResponse[String]): Either[BrazeError, BrazeResponse] = {
    decode[BrazeResponse](response.body) match {
      case Right(parsedResponse) if response.is2xx  =>
        Right(parsedResponse)
      case Left(e) if response.is2xx =>
        logger.error("failure to parse Braze response", e)
        Left(BrazeError(response))
      case _ =>
        Left(BrazeError(response))
    }
  }

  def updateUser(apiKey: String, request: UserTrackRequest): Either[BrazeError, BrazeResponse] = {
    withClientLogging(s"updating user: ${request.attributes.toString()}, ${request.events.toString()}"){
      val response = Http(s"$brazeEndpoint/users/track")
        .timeout(connTimeoutMs = timeout, readTimeoutMs = timeout)
        .header("Content-type", "application/json")
        .header("Authorization", s"Bearer $apiKey")
        .postData(request.asJson.noSpaces)
        .asString

      parseValidateResponse(response)
    }
  }
}
