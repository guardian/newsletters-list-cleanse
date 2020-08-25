package com.gu.newsletterlistcleanse

import java.util.concurrent.TimeUnit

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

import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.JavaConverters._
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

object GetCleanseListLambda {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)
  val campaigns: Campaigns = new CampaignsFromDB()

  val timeout: Duration = Duration(15, TimeUnit.MINUTES)

  def handler(sqsEvent: SQSEvent): Unit = {
    parseSqsMessage(sqsEvent) match {
      case Right(cleanseLists) =>
        Await.result(process(cleanseLists), timeout)
      case Left(parseError) =>
        logger.error(parseError.getMessage)
    }
  }

  def parseSqsMessage(sqsEvent: SQSEvent): Either[circe.Error, List[NewsletterCutOff]] = {
    (for {
      message <- sqsEvent.getRecords.asScala.toList
    } yield {
      decode[NewsletterCutOff](message.getBody)
    }).toEitherList
  }

  def sendCleanseList(queueName: QueueName, cleanseList: CleanseList): Future[SendMessageResult] = {
    AwsSQSSend.sendMessage(queueName, Payload(cleanseList.asJson.noSpaces))
  }

  def process(campaignCutOffDates: List[NewsletterCutOff]): Future[List[SendMessageResult]]  = {
    val env = Env()
    logger.info(s"Starting $env")
    val queueName = QueueName(s"newsletter-cleanse-list-${env.stage}")

    val results = for {
      campaignCutOff <- campaignCutOffDates
      userIds = campaigns.fetchCampaignCleanseList(campaignCutOff).map(_.userId)
      cleanseList = CleanseList(
        campaignCutOff.newsletterName,
        userIds
      )
      batchedCleanseList = cleanseList.getCleanseListBatches(5000)
      (batch, index) <- batchedCleanseList.zipWithIndex
    } yield {
      logger.info(s"Sending batch $index of ${batch.newsletterName} to ${queueName.value}")
      sendCleanseList(queueName, batch)
    }

    Future.sequence(results)
  }
}

object TestGetCleanseList {
  def main(args: Array[String]): Unit = {
    val json = """{"newsletterName":"Editorial_AnimalsFarmed","cutOffDate":"2020-01-21T11:31:14Z[Europe/London]"}"""
    val parsedJson = decode[NewsletterCutOff](json).right.get
    Await.result(GetCleanseListLambda.process(List(parsedJson)), GetCleanseListLambda.timeout)
  }
}
