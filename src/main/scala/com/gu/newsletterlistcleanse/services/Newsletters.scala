package com.gu.newsletterlistcleanse.services

import java.time.ZonedDateTime

import cats.implicits._
import cats.data.EitherT
import com.gu.identity.model.{EmailNewsletter, EmailNewsletters}
import com.gu.newsletterlistcleanse.db.ActiveListLength.getActiveListLength
import com.gu.newsletterlistcleanse.db.{ActiveListLength, CampaignSentDate}
import com.gu.newsletterlistcleanse.models.NewsletterCutOff
import io.circe.Decoder
import sttp.client.asynchttpclient.WebSocketHandler
import sttp.client.{SttpBackend, basicRequest}

import scala.concurrent.duration._
import io.circe.parser.decode
import io.circe.generic.semiauto._
import sttp.client._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

case class Newsletter(brazeNewsletterName: String)

object Newsletter {
  implicit val newsletterDecoder: Decoder[Newsletter] = deriveDecoder[Newsletter]
}

class Newsletters {
  val timeout: FiniteDuration = 5000.seconds

  implicit val sttpBackend: SttpBackend[Future, Nothing, WebSocketHandler] = SttpFactory.createSttpBackend()

  def fetchAllNewsletters(): EitherT[Future, String, List[String]] = {
    def parseBody(bodyString: String): Either[String, List[String]] = {
      decode[List[Newsletter]](bodyString) match {
        case Left(error) => Left(error.getMessage)
        case Right(body) => Right(body.map(_.brazeNewsletterName))
      }
    }

    val response = basicRequest
      .get(uri"https://idapi.theguardian.com/newsletters")
      .header("Origin", "https://www.theguardian.com")
      .readTimeout(timeout)
      .send()

    for {
      body <- EitherT(response.map(_.body))
      parsedBody <- EitherT.fromEither[Future](parseBody(body))
    } yield parsedBody
  }

  private val reverseChrono: Ordering[ZonedDateTime] = (x: ZonedDateTime, y: ZonedDateTime) => y.compareTo(x)

  def computeCutOffDates(campaignSentDates: List[CampaignSentDate], listLengths: List[ActiveListLength]): List[NewsletterCutOff] = {

    def extractCutOffBasedOnCampaign(campaignName: String, sentDates: List[CampaignSentDate]): Option[NewsletterCutOff] = for {

      unOpenCount <- Newsletters.cleansingPolicy.get(campaignName)
      activeCount = getActiveListLength(listLengths, campaignName)
      cutOff <- sentDates
        .sortBy(_.timestamp)(reverseChrono)
        .drop(unOpenCount - 1)
        .headOption
        .map(send => NewsletterCutOff(campaignName, send.timestamp, activeCount))
    } yield cutOff

    campaignSentDates.groupBy(_.campaignName)
      .toList
      .flatMap { case (campaignName, sentDates) => extractCutOffBasedOnCampaign(campaignName, sentDates) }
  }
}

object Newsletters {

  val cleansingPolicy: Map[String, Int] = Map(
    "Editorial_GuardianTodayUK" -> 94,
    "Editorial_GuardianTodayUS" -> 94,
    "Editorial_GuardianTodayAUS" -> 94,
    "Editorial_MorningBriefingUK" -> 94,
    "Editorial_USMorningBriefing" -> 61,
    "Editorial_MorningMailAUS" -> 61,
    //"Editorial_CoronavirusAustraliaAtAGlance" -> 94, => probably too recent to require cleaning
    "Editorial_GlobalDispatch" -> 13,
    "Editorial_TheUSPoliticsMinute" -> 37,
    "Editorial_AustralianPolitics" -> 61,
    "Editorial_BusinessToday" -> 61,
    "Editorial_GreenLight" -> 13,
    "Editorial_LabNotes" -> 13,
    "Editorial_GuardianDocumentaries" -> 7,
    "Editorial_TheLongRead" -> 13,
    "Editorial_AnimalsFarmed" -> 7,
    "Editorial_GunsAndLiesInAmerica" -> 7,
    "Editorial_TheUpside" -> 13,
    "Editorial_ThisLandIsYourLand" -> 7,
    "Editorial_TheRecap" -> 13,
    "Editorial_TheFiver" -> 61,
    "Editorial_TheBreakdown" -> 13,
    "Editorial_TheSpin" -> 13,
    "Editorial_GuardianAustraliaSports" -> 94,
    "Editorial_SleeveNotes" -> 13,
    "Editorial_FilmToday" -> 61,
    "Editorial_Bookmarks" -> 13,
    "Editorial_ArtWeekly" -> 13,
    "Editorial_HearHere" -> 13,
    "Editorial_TheFlyer" -> 13,
    "Editorial_FashionStatement" -> 13,
    "Editorial_TheGuideStayingIn" -> 13,
    "Editorial_SavedForLater" -> 13,
    "Editorial_WordOfMouth" -> 13,
    "Editorial_BestOfGuardianOpinionUK" -> 61,
    "Editorial_ThisIsEurope" -> 13,
    "Editorial_BestOfGuardianOpinionUS" -> 61,
    "Editorial_TheWeekInPatriarchy" -> 13,
    "Editorial_BestOfGuardianOpinionAUS" -> 61,
    "Editorial_FirstDogOnTheMoon" -> 37,
    "Editorial_BusinessView" -> 13,
    "Editorial_GuardianStudentsNetwork" -> 7,
    "Editorial_GuardianUniversities" -> 13,
    "Editorial_SocietyWeekly" -> 13,
    "Editorial_TeacherNetwork" -> 13,
    "Editorial_DesignReview" -> 7,
    // "Editorial_WeekendPapers" -> 94, => Marketing
    // "Editorial_OFM" -> 94, => Marketing
    // "MK_FrontPage" -> 94, => Marketing
  )

  val maxCutOffPeriod = cleansingPolicy.valuesIterator.max

  val guardianTodayUK = "Editorial_GuardianTodayUK"
  val guardianTodayUKCampaigns = List("Editorial_GuardianTodayUK_Weekend", "Editorial_GuardianTodayUK_Weekdays")

  def getIdentityNewsletterFromName(newsletterName: String): Option[EmailNewsletter] =
    EmailNewsletters.allNewsletters.find(_.brazeNewsletterName == newsletterName)
}
