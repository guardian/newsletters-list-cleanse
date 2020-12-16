package com.gu.newsletterlistcleanse

import java.util.concurrent.TimeUnit
import cats.implicits._
import cats.data.EitherT
import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.sqs.AmazonSQSAsync
import com.amazonaws.services.sqs.model.SendMessageResult
import com.gu.newsletterlistcleanse.db.{BigQueryOperations, DatabaseOperations}
import com.gu.newsletterlistcleanse.models.{BrazeData, NewsletterCutOffWithBraze}
import com.gu.newsletterlistcleanse.services.{Newsletter, Newsletters}
import com.gu.newsletterlistcleanse.sqs.AwsSQSSend
import com.gu.newsletterlistcleanse.sqs.AwsSQSSend.Payload
import org.slf4j.{Logger, LoggerFactory}
import io.circe.syntax.EncoderOps

import scala.beans.BeanProperty
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}

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
    val result = Try(Await.result(process(lambdaInput).value, timeout))

    result match {
      case Success(Right(results)) => logger.info(s"Sent ${results.length} messages to SQS")
      case Success(Left(error)) =>
        logger.error(s"An error has occurred during the execution of the function: $error")
        throw new RuntimeException(s"An error has occurred during the execution of the function: $error")
      case Failure(exception) =>
        logger.error(s"An error has occurred during the execution of the function", exception)
        throw exception
    }
  }

  def process(lambdaInput: GetCutOffDatesLambdaInput): EitherT[Future, String, List[SendMessageResult]] = {
    val env = Env()
    logger.info(s"Starting $env")
    for {
      newslettersToProcess <- fetchNewsletters(lambdaInput.newslettersToProcess.toList)
      cutOffDates = calculateCutOffDates(newslettersToProcess)
      _ = logger.info(s"result: ${cutOffDates.asJson.noSpaces}")
      result <- EitherT.liftF(sendCutOffs(cutOffDates))
    } yield result
  }

  def fetchNewsletters(newslettersToProcess: List[String]): EitherT[Future, String, List[Newsletter]] = {
    if (newslettersToProcess.nonEmpty) {
      newsletters.fetchNewsletters(newslettersToProcess)
    } else {
      newsletters.fetchNewsletters()
    }
  }

  def calculateCutOffDates(newslettersToProcess: List[Newsletter]): List[NewsletterCutOffWithBraze] = {
    val newsletterNamesToProcess = newslettersToProcess.map(_.brazeNewsletterName)
    val listLengths = databaseOperations.fetchCampaignActiveListLength(newsletterNamesToProcess)
    val campaignSentDates = databaseOperations.fetchCampaignSentDates(newsletterNamesToProcess, Newsletters.maxCutOffPeriod)
    val guardianTodayUKSentDates = if (newsletterNamesToProcess.contains(Newsletters.guardianTodayUK)) {
      databaseOperations.fetchGuardianTodayUKSentDates(Newsletters.maxCutOffPeriod)
    } else Nil
    val cutOffs = newsletters.computeCutOffDates(campaignSentDates ++ guardianTodayUKSentDates, listLengths)
    val result = for {
      cutOff <- cutOffs
      newsletter <- newslettersToProcess.find(newsletter => newsletter.brazeNewsletterName == cutOff.newsletterName)
      brazeData = BrazeData(newsletter.brazeSubscribeAttributeName, newsletter.brazeSubscribeEventNamePrefix)
    } yield
      NewsletterCutOffWithBraze(cutOff, brazeData)


    println(result.mkString(", "))
    result

  }

  def sendCutOffs(cutOffDates: List[NewsletterCutOffWithBraze]): Future[List[SendMessageResult]] = {
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
    val lambdaInput = GetCutOffDatesLambdaInput(Array.empty)
    val result = Try(Await.result(getCutOffDatesLambda.process(lambdaInput).value, getCutOffDatesLambda.timeout))

    result match {
      case Success(Right(results)) => println(s"Sent ${results.length} messages to SQS")
      case Success(Left(error)) =>
        println(s"An error has occurred during the execution of the function: $error")
        throw new RuntimeException(s"An error has occurred during the execution of the function: $error")
      case Failure(exception) =>
        println(s"An error has occurred during the execution of the function", exception)
        throw exception
    }
    getCutOffDatesLambda.sqsClient.shutdown()
  }
}
