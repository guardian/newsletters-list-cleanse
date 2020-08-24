package com.gu.newsletterlistcleanse

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.amazonaws.services.sqs.model.SendMessageResult
import com.gu.newsletterlistcleanse.db.{Campaigns, CampaignsFromDB}
import com.gu.newsletterlistcleanse.models.{CleanseList, NewsletterCutOff}
import com.gu.newsletterlistcleanse.sqs.AwsSQSSend
import com.gu.newsletterlistcleanse.sqs.AwsSQSSend.{Payload, QueueName}
import com.gu.newsletterlistcleanse.EitherConverter.EitherList
import io.circe
import io.circe.parser._
import io.circe.syntax._

import scala.collection.JavaConverters._
import scala.beans.BeanProperty
import org.slf4j.{Logger, LoggerFactory}

object GetCleanseListLambda {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)
  val campaigns: Campaigns = new CampaignsFromDB()


  def handler(sqsEvent: SQSEvent) = {
    parseSqsMessage(sqsEvent) match {
      case Right(cleanseLists) =>
        cleanseLists.foreach(process)
      case Left(parseError) =>
        logger.error(parseError.getMessage)
    }
  }


  def parseSqsMessage(sqsEvent: SQSEvent): Either[circe.Error, List[List[NewsletterCutOff]]] = {
    (for {
      // We only get a single message here despite it being a list
      message <- sqsEvent.getRecords().asScala.toList
    } yield {
      decode[List[NewsletterCutOff]](message.getBody())
    }).toEitherList
  }

  def sendCleanseList(queueName: QueueName, cleanseList: CleanseList): SendMessageResult = {
    AwsSQSSend.sendMessage(queueName, Payload(cleanseList.asJson.noSpaces))
  }

  def process(campaignCutOffDates: List[NewsletterCutOff]): Unit  = {
    val env = Env()
    logger.info(s"Starting $env")
    val queueName = QueueName(s"newsletter-cleanse-list-CODE")

    for {
      campaignCutOff <- campaignCutOffDates
      userIds = campaigns.fetchCampaignCleanseList(campaignCutOff).map(_.userId)
      cleanseList = CleanseList(
        campaignCutOff.newsletterName,
        userIds
      )
      batchedCleanseList = cleanseList.getCleanseListBatches(5000)
      (batch, index) <- batchedCleanseList.zipWithIndex
    } {
      logger.info(s"Sending batch $index of ${batch.newsletterName} to ${queueName.value}")
      sendCleanseList(queueName, batch) // Do we want to return the SendMessageResult?
    }
  }
}

object TestGetCleanseList {
  def main(args: Array[String]): Unit = {
    val JsonString = "[{\"newsletterName\":\"Editorial_AnimalsFarmed\",\"cutOffDate\":\"2020-01-21T11:31:14Z[Europe/London]\"},{\"newsletterName\":\"Editorial_TheLongRead\",\"cutOffDate\":\"2020-05-16T09:00:26+01:00[Europe/London]\"}]"
    GetCleanseListLambda.process(decode[List[NewsletterCutOff]](JsonString).toOption.map(cutOff => cutOff).toList.flatten)
  }
}
