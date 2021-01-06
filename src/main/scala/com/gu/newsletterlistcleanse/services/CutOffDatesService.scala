package com.gu.newsletterlistcleanse.services

import java.time.ZonedDateTime

import cats.implicits._
import com.gu.newsletterlistcleanse.EitherConverter._
import com.gu.newsletterlistcleanse.db.ActiveListLength.getActiveListLength
import com.gu.newsletterlistcleanse.db.{ActiveListLength, CampaignSentDate, DatabaseOperations}
import com.gu.newsletterlistcleanse.models.{BrazeData, Newsletter, NewsletterCutOff, NewsletterCutOffWithBraze, Newsletters}
import org.slf4j.{Logger, LoggerFactory}


class CutOffDatesService(databaseOperations: DatabaseOperations) {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)

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
    val cutOffs = computeCutOffDates(campaignSentDates ++ guardianTodayUKSentDates, listLengths)

    val result = cutOffs.map(addBrazeData)
    result.toEitherList.leftMap(_.mkString(", "))
  }

  private val reverseChrono: Ordering[ZonedDateTime] = (x: ZonedDateTime, y: ZonedDateTime) => y.compareTo(x)

  def computeCutOffDates(
    campaignSentDates: List[CampaignSentDate],
    listLengths: List[ActiveListLength]
  ): List[NewsletterCutOff] = {

    def extractCutOffBasedOnCampaign(
      campaignName: String,
      sentDates: List[CampaignSentDate]): Option[NewsletterCutOff] =
      for {
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
      .flatMap { case (campaignName, sentDates ) => extractCutOffBasedOnCampaign(campaignName, sentDates) }
  }
}
