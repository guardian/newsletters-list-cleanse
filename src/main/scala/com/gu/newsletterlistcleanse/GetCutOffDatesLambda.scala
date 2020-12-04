package com.gu.newsletterlistcleanse

import java.util.concurrent.TimeUnit

import cats.implicits._
import cats.data.EitherT
import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.sqs.AmazonSQSAsync
import com.amazonaws.services.sqs.model.SendMessageResult
import com.gu.newsletterlistcleanse.db.{BigQueryOperations, DatabaseOperations}
import com.gu.newsletterlistcleanse.models.NewsletterCutOff
import com.gu.newsletterlistcleanse.services.Newsletters
import com.gu.newsletterlistcleanse.sqs.AwsSQSSend
import com.gu.newsletterlistcleanse.sqs.AwsSQSSend.Payload
import org.slf4j.{Logger, LoggerFactory}
import io.circe.syntax.EncoderOps

import scala.beans.BeanProperty
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global

case class GetCutOffDatesLambdaInput(
  @BeanProperty
  var newslettersToProcess: Array[String]
) {
  // set a default constructor so Jackson is able to instantiate the class as a java bean
  def this() = this(
    newslettersToProcess = new Array[String](0)
  )
}

class GetCutOffDatesLambda {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)
  val credentialProvider: AWSCredentialsProvider = new NewsletterSQSAWSCredentialProvider()
  val sqsClient: AmazonSQSAsync = AwsSQSSend.buildSqsClient(credentialProvider)
  val config: NewsletterConfig = NewsletterConfig.load(credentialProvider)
  val databaseOperations: DatabaseOperations = new BigQueryOperations(config.serviceAccount, config.projectId)
  val newsletters: Newsletters = new Newsletters()

  val timeout: Duration = Duration(15, TimeUnit.MINUTES)

  def handler(lambdaInput: GetCutOffDatesLambdaInput, context: Context): Unit = {
    val result = Await.result(process(lambdaInput).value, timeout)

    result match {
      case Right(results) => logger.info(s"Sent ${results.length} messages to SQS")
      case Left(error) => throw new RuntimeException(s"An error has occurred during the execution of the function: $error")
    }
  }

  def process(lambdaInput: GetCutOffDatesLambdaInput): EitherT[Future, String, List[SendMessageResult]] = {
    val env = Env()
    logger.info(s"Starting $env")
    for {
      newslettersToProcess <- fetchAllNewsletter(lambdaInput.newslettersToProcess.toList)
      cutOffDates = calculateCutOffDates(newslettersToProcess)
      _ = logger.info(s"result: ${cutOffDates.asJson.noSpaces}")
      result <- EitherT.liftF(sendCutOffs(cutOffDates))
    } yield result
  }

  def fetchAllNewsletter(newslettersToProcess: List[String]): EitherT[Future, String, List[String]] = {
    if (newslettersToProcess.nonEmpty) {
      EitherT.pure[Future, String](newslettersToProcess)
    } else {
      newsletters.fetchAllNewsletters()
    }
  }

  def calculateCutOffDates(newslettersToProcess: List[String]): List[NewsletterCutOff] = {
    val listLengths = databaseOperations.fetchCampaignActiveListLength(newslettersToProcess)
    val campaignSentDates = databaseOperations.fetchCampaignSentDates(newslettersToProcess, Newsletters.maxCutOffPeriod)
    val guardianTodayUKSentDates = if (newslettersToProcess.contains(Newsletters.guardianTodayUK)) {
      databaseOperations.fetchGuardianTodayUKSentDates(Newsletters.maxCutOffPeriod)
    } else Nil
    newsletters.computeCutOffDates(campaignSentDates ++ guardianTodayUKSentDates, listLengths)
  }

  def sendCutOffs(cutOffDates: List[NewsletterCutOff]): Future[List[SendMessageResult]] = {
    val results = cutOffDates.map { cutoffDate =>
      logger.info(s"Sending cut-off date: $cutoffDate")
      AwsSQSSend.sendMessage(sqsClient, config.cutOffSqsUrl, Payload(cutoffDate.asJson.noSpaces))
    }
    Future.sequence(results)
  }
}

object TestGetCutOffDates {
  def main(args: Array[String]): Unit = {
    val getCutOffDatesLambda = new GetCutOffDatesLambda()
    val lambdaInput = GetCutOffDatesLambdaInput(Array("Editorial_AnimalsFarmed", "Editorial_TheLongRead"))
    Await.result(getCutOffDatesLambda.process(lambdaInput).value, getCutOffDatesLambda.timeout)
    getCutOffDatesLambda.sqsClient.shutdown()
  }
}
