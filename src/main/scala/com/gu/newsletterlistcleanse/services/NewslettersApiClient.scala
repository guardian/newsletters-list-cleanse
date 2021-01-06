package com.gu.newsletterlistcleanse.services

import cats.implicits._
import cats.data.EitherT
import com.gu.newsletterlistcleanse.models.Newsletter
import sttp.client.asynchttpclient.WebSocketHandler
import sttp.client.{SttpBackend, basicRequest}

import scala.concurrent.duration._
import io.circe.parser.decode
import sttp.client._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration


class NewslettersApiClient {
  val timeout: FiniteDuration = 5.seconds

  implicit val sttpBackend: SttpBackend[Future, Nothing, WebSocketHandler] = SttpFactory.createSttpBackend()

  def filterNewsletters(allNewsletters: List[Newsletter], filterList: List[String] = Nil): List[Newsletter] =
    filterList match {
      case Nil =>
        allNewsletters
      case _ =>
        allNewsletters.filter(newsletter => filterList.contains(newsletter.brazeNewsletterName))
    }

  def fetchNewsletters(filterList: List[String] = Nil): EitherT[Future, String, List[Newsletter]] = {
    def parseBody(bodyString: String): Either[String, List[Newsletter]] = {
      decode[List[Newsletter]](bodyString) match {
        case Left(error) => Left(error.getMessage)
        case Right(body) => Right(filterNewsletters(body, filterList))
      }
    }

    def handleNon200(response: Response[Either[String, String]]): Either[String, String] = response.body match {
      case Right(body) if (response.code.isSuccess) => Right(body)
      case _ => Left(s"${response.code.code}: ${response.statusText}")
    }

    val response = basicRequest
      .get(uri"https://idapi.theguardian.com/newsletters")
      .header("Origin", "https://www.theguardian.com")
      .readTimeout(timeout)
      .send()

    for {
      body <- EitherT(response.map(handleNon200))
      parsedBody <- EitherT.fromEither[Future](parseBody(body))
    } yield parsedBody
  }
}
