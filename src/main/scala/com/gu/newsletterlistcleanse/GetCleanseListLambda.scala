package com.gu.newsletterlistcleanse

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.gu.newsletterlistcleanse.db.{Campaigns, CampaignsFromDB}
import com.gu.newsletterlistcleanse.models.{CleanseList, NewsletterCutOff}
import com.gu.newsletterlistcleanse.sqs.AwsSQSSend
import com.gu.newsletterlistcleanse.sqs.AwsSQSSend.{QueueName, Payload}
import io.circe.parser._
import io.circe.syntax._

import scala.collection.JavaConverters._
import scala.beans.BeanProperty
import org.slf4j.{Logger, LoggerFactory}


case class GetCleanseListLambdaInput(
  @BeanProperty
  cutOffDates: SQSEvent)

object GetCleanseListLambda {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)
  val campaigns: Campaigns = new CampaignsFromDB()


  def handler(lambdaInput: GetCleanseListLambdaInput): Unit = {
    val cutOffDates = parseSqsMessage(lambdaInput)
    process(cutOffDates)
  }

  def parseSqsMessage(lambdaInput: GetCleanseListLambdaInput): List[NewsletterCutOff] =
    (for (message <- lambdaInput.cutOffDates.getRecords().asScala;
      cutOff <- decode[List[NewsletterCutOff]](message.getBody()).toOption)
      yield {
          cutOff
      }).toList.flatten

  def cleanseListToJsonString(cleanseList: CleanseList): String = cleanseList.asJson.noSpaces


  def process(campaignCutOffDates: List[NewsletterCutOff]): Unit = {
    val env = Env()
    logger.info(s"Starting $env")
    val queueName = QueueName(s"newsletter-cleanse-list-CODE")

    for (
      campaignCutOff <- campaignCutOffDates;
      cleanseList = CleanseList(
        campaignCutOff.newsletterName,
        campaigns.fetchCampaignCleanseList(campaignCutOff).map(userID => userID.userId
        )
      );
      batchedCleanseList = CleanseListHandler(cleanseList).getCleanseListBatches(5000);
      (batch, index) <- batchedCleanseList.zipWithIndex
    ){
      logger.info(s"Sending batch $index of ${batch.newsletterName} to $queueName")
      AwsSQSSend.sendMessage(queueName, Payload(cleanseListToJsonString(batch)))
    }
  }
}

object TestGetCleanseList {
  def main(args: Array[String]): Unit = {
    val JsonString = "[{\"newsletterName\":\"Editorial_AnimalsFarmed\",\"cutOffDate\":\"2020-01-21T11:31:14Z[Europe/London]\"},{\"newsletterName\":\"Editorial_TheLongRead\",\"cutOffDate\":\"2020-05-16T09:00:26+01:00[Europe/London]\"}]"
    GetCleanseListLambda.process(decode[List[NewsletterCutOff]](JsonString).toOption.map(cutOff => cutOff).toList.flatten)
  }
}
