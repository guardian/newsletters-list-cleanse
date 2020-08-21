package com.gu.newsletterlistcleanse

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.sqs.model.SendMessageResult
import com.gu.newsletterlistcleanse.db.{Campaigns, CampaignsFromDB}
import com.gu.newsletterlistcleanse.models.NewsletterCutOff
import com.gu.newsletterlistcleanse.sqs.AwsSQSSend
import com.gu.newsletterlistcleanse.sqs.AwsSQSSend.{Payload, QueueName}
import org.slf4j.{Logger, LoggerFactory}
import io.circe.syntax._

import scala.beans.BeanProperty


case class GetCutOffDatesLambdaInput(
  @BeanProperty
  newslettersToProcess: List[String]
)

object GetCutOffDatesLambda {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)
  val campaigns: Campaigns = new CampaignsFromDB()
  val newsletters: Newsletters = new Newsletters()

  def handler(lambdaInput: GetCutOffDatesLambdaInput, context: Context): Unit = {
    process(lambdaInput)
  }

  def sendCutOffDates(queueName: QueueName, cutOffDates: List[NewsletterCutOff]): SendMessageResult = {
    AwsSQSSend.sendMessage(queueName, Payload(cutOffDates.asJson.noSpaces))
  }

  def process(lambdaInput: GetCutOffDatesLambdaInput): Unit = {
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
    GetCutOffDatesLambda.process(GetCutOffDatesLambdaInput(List("Editorial_AnimalsFarmed", "Editorial_TheLongRead")))
  }
}
