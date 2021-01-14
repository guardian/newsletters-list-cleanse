package com.gu.newsletterlistcleanse.services

import java.time.LocalDate

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.s3.AmazonS3
import com.gu.newsletterlistcleanse.db.DatabaseOperations
import com.gu.newsletterlistcleanse.models.{CleanseList, NewsletterCutOff, NewsletterCutOffWithBraze, Newsletters}
import com.gu.newsletterlistcleanse.{Env, NewsletterConfig}
import io.circe.syntax._
import org.slf4j.{Logger, LoggerFactory}

class CleanseListService(config: NewsletterConfig, s3Client: AmazonS3, databaseOperations: DatabaseOperations) {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  def fetchCampaignCleanseList(campaignCutOff: NewsletterCutOff): List[String] = {
    if (campaignCutOff.newsletterName == Newsletters.guardianTodayUK) {
      databaseOperations.fetchGuardianTodayUKCleanseList(campaignCutOff).map(_.userId)
    } else {
      databaseOperations.fetchCampaignCleanseList(campaignCutOff).map(_.userId)
    }
  }

  def exportCleanseListToS3(cleanseList: CleanseList, env: Env, contextOption: Option[Context]): Unit = {
    val exportJson = cleanseList.asJson.toString
    val date = LocalDate.now().toString
    contextOption.foreach { context =>
      val key = s"${env.stage}/$date/${cleanseList.newsletterName}.${context.getAwsRequestId}.json"
      s3Client.putObject(config.backupBucketName, key, exportJson)
    }
  }

  def fetchCleanseLists(campaignCutOffDates: List[NewsletterCutOffWithBraze], contextOption: Option[Context], env: Env): List[CleanseList]  = {

    for {
      campaignCutOffWithBraze <- campaignCutOffDates
      _ = logger.info(s"Fetching cleanse list for ${campaignCutOffWithBraze.newsletterCutOff.newsletterName}")
      campaignCutOff = campaignCutOffWithBraze.newsletterCutOff
      brazeData = campaignCutOffWithBraze.brazeData
      userIds = fetchCampaignCleanseList(campaignCutOff)
    } yield {
      val cleanseList = CleanseList(
        newsletterName = campaignCutOff.newsletterName,
        activeListLength = campaignCutOff.activeListLength,
        deletionCandidates = userIds.length,
        userIdList = userIds,
        brazeData = brazeData,
      )
      logger.info(s"Found ${userIds.length} users of ${campaignCutOff.activeListLength} to remove from ${campaignCutOff.newsletterName}")
      exportCleanseListToS3(cleanseList, env, contextOption)
      cleanseList
    }
  }
}
