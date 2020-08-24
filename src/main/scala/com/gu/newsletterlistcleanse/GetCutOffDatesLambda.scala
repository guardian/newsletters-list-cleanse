package com.gu.newsletterlistcleanse

import java.util.concurrent.TimeUnit

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.sqs.model.SendMessageResult
import com.gu.newsletterlistcleanse.db.{Campaigns, CampaignsFromDB}
import com.gu.newsletterlistcleanse.models.NewsletterCutOff
import com.gu.newsletterlistcleanse.sqs.AwsSQSSend
import com.gu.newsletterlistcleanse.sqs.AwsSQSSend.{Payload, QueueName}
import org.slf4j.{Logger, LoggerFactory}
import io.circe.syntax._

import scala.beans.BeanProperty
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global

case class GetCutOffDatesLambdaInput(
  @BeanProperty
  newslettersToProcess: List[String]
)

object GetCutOffDatesLambda {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)
  val campaigns: Campaigns = new CampaignsFromDB()
  val newsletters: Newsletters = new Newsletters()

  val timeout: Duration = Duration(15, TimeUnit.MINUTES)

  def handler(lambdaInput: GetCutOffDatesLambdaInput, context: Context): Unit = {
    Await.result(process(lambdaInput), timeout)
  }

  def sendCutOffDates(queueName: QueueName, cutOffDates: List[NewsletterCutOff]): Future[List[SendMessageResult]] = {
    val results = cutOffDates.map { cutoffDate =>
      logger.info(s"Sending cut-off date: $cutoffDate")
      AwsSQSSend.sendMessage(queueName, Payload(cutoffDate.asJson.noSpaces))
    }

    Future.sequence(results)
  }

  def process(lambdaInput: GetCutOffDatesLambdaInput): Future[List[SendMessageResult]] = {
    val env = Env()
    logger.info(s"Starting $env")
    val newslettersToProcess = Option(lambdaInput.newslettersToProcess) // this is set by AWS, so potentially null
      .getOrElse(newsletters.allNewsletters)
    val campaignSentDates = campaigns.fetchCampaignSentDates(newslettersToProcess, Newsletters.maxCutOffPeriod)
    val cutOffDates = newsletters.computeCutOffDates(campaignSentDates)
    logger.info(s"result: ${cutOffDates.asJson.noSpaces}")
    val queueName = QueueName(s"newsletter-newsletter-cut-off-date-${env.stage}")
    sendCutOffDates(queueName, cutOffDates)
  }
}

object TestGetCutOffDates {
  def main(args: Array[String]): Unit = {
    val lambdaInput = GetCutOffDatesLambdaInput(List("Editorial_AnimalsFarmed", "Editorial_TheLongRead"))
    Await.result(GetCutOffDatesLambda.process(lambdaInput), GetCutOffDatesLambda.timeout)
  }
}
