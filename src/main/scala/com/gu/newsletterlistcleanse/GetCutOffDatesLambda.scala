package com.gu.newsletterlistcleanse

import java.util.concurrent.TimeUnit

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.sqs.AmazonSQSAsync
import com.amazonaws.services.sqs.model.SendMessageResult
import com.gu.newsletterlistcleanse.db.{BigQueryOperations, DatabaseOperations}
import com.gu.newsletterlistcleanse.models.NewsletterCutOff
import com.gu.newsletterlistcleanse.sqs.AwsSQSSend
import com.gu.newsletterlistcleanse.sqs.AwsSQSSend.Payload
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

class GetCutOffDatesLambda {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)
  val credentialProvider: AWSCredentialsProvider = new NewsletterSQSAWSCredentialProvider()
  val sqsClient: AmazonSQSAsync = AwsSQSSend.buildSqsClient(credentialProvider)
  val config: NewsletterConfig = NewsletterConfig.load(credentialProvider)
  val databaseOperations: DatabaseOperations = new BigQueryOperations(config.serviceAccount, config.projectId)
  val newsletters: Newsletters = new Newsletters()

  val timeout: Duration = Duration(15, TimeUnit.MINUTES)

  def handler(lambdaInput: GetCutOffDatesLambdaInput, context: Context): Unit = {
    Await.result(process(lambdaInput), timeout)
  }

  def sendCutOffDates(cutOffDates: List[NewsletterCutOff]): Future[List[SendMessageResult]] = {
    val results = cutOffDates.map { cutoffDate =>
      logger.info(s"Sending cut-off date: $cutoffDate")
      AwsSQSSend.sendMessage(sqsClient, config.cutOffSqsUrl, Payload(cutoffDate.asJson.noSpaces))
    }

    Future.sequence(results)
  }

  def process(lambdaInput: GetCutOffDatesLambdaInput): Future[List[SendMessageResult]] = {
    val env = Env()
    logger.info(s"Starting $env")
    val newslettersToProcess = Option(lambdaInput.newslettersToProcess) // this is set by AWS, so potentially null
      .getOrElse(newsletters.allNewsletters)
    val campaignSentDates = databaseOperations.fetchCampaignSentDates(newslettersToProcess, Newsletters.maxCutOffPeriod)
    val cutOffDates = newsletters.computeCutOffDates(campaignSentDates)
    logger.info(s"result: ${cutOffDates.asJson.noSpaces}")
    sendCutOffDates(cutOffDates)
  }
}

object TestGetCutOffDates {
  def main(args: Array[String]): Unit = {
    val getCutOffDatesLambda = new GetCutOffDatesLambda()
    val lambdaInput = GetCutOffDatesLambdaInput(List("Editorial_AnimalsFarmed", "Editorial_TheLongRead"))
    Await.result(getCutOffDatesLambda.process(lambdaInput), getCutOffDatesLambda.timeout)
  }
}
