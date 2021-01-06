package com.gu.newsletterlistcleanse

import cats.implicits._
import cats.data.EitherT
import com.gu.newsletterlistcleanse.db.DatabaseOperations
import com.gu.newsletterlistcleanse.models.{BrazeData, NewsletterCutOff, NewsletterCutOffWithBraze}
import com.gu.newsletterlistcleanse.services.{Newsletter, Newsletters}
import com.gu.newsletterlistcleanse.EitherConverter._
import org.slf4j.{Logger, LoggerFactory}
import io.circe.syntax.EncoderOps

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class GetCutOffDatesLambda(databaseOperations: DatabaseOperations) {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)
  val newsletters: Newsletters = new Newsletters()

  def fetchAndCalculateCutOffDates(newslettersToProcess: List[String]): EitherT[Future, String, List[NewsletterCutOffWithBraze]] = {

    for {
      newslettersToProcess <- fetchNewsletters(newslettersToProcess)
      cutOffDates <- EitherT.fromEither[Future](calculateCutOffDates(newslettersToProcess))
    } yield {
      logger.info(s"result: ${cutOffDates.asJson.noSpaces}")
      cutOffDates
    }
  }

  def fetchNewsletters(newslettersToProcess: List[String]): EitherT[Future, String, List[Newsletter]] = {
    if (newslettersToProcess.nonEmpty) {
      newsletters.fetchNewsletters(newslettersToProcess)
    } else {
      newsletters.fetchNewsletters()
    }
  }

  def calculateCutOffDates(newslettersToProcess: List[Newsletter]): Either[String, List[NewsletterCutOffWithBraze]] = {

    def addBrazeData(cutOff: NewsletterCutOff): Either[String, NewsletterCutOffWithBraze] = {
      newslettersToProcess
        .find(newsletter => newsletter.brazeNewsletterName == cutOff.newsletterName)
        .toRight(s"Couldn't find ${cutOff.newsletterName}")
        .map { newsletter =>
          val brazeData = BrazeData(newsletter.brazeSubscribeAttributeName, newsletter.brazeSubscribeEventNamePrefix)
          val newsletterCutOffWithBraze = NewsletterCutOffWithBraze(cutOff, brazeData)
          logger.info(s"Added BrazeData to ${cutOff.newsletterName}:\t${newsletterCutOffWithBraze.toString}")
          newsletterCutOffWithBraze
        }
      }

    val newsletterNamesToProcess = newslettersToProcess.map(_.brazeNewsletterName)
    val listLengths = databaseOperations.fetchCampaignActiveListLength(newsletterNamesToProcess)
    val campaignSentDates = databaseOperations.fetchCampaignSentDates(newsletterNamesToProcess, Newsletters.maxCutOffPeriod)
    val guardianTodayUKSentDates = if (newsletterNamesToProcess.contains(Newsletters.guardianTodayUK)) {
      databaseOperations.fetchGuardianTodayUKSentDates(Newsletters.maxCutOffPeriod)
    } else Nil
    val cutOffs = newsletters.computeCutOffDates(campaignSentDates ++ guardianTodayUKSentDates, listLengths)

    val result = cutOffs.map(addBrazeData)
    result.toEitherList.leftMap(_.mkString(", "))

  }
}
