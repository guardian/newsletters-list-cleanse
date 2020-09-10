package com.gu.newsletterlistcleanse

import java.time.LocalDate
import java.util.concurrent.TimeUnit

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import com.amazonaws.regions.Regions
import com.amazonaws.services.sqs.AmazonSQSAsync
import com.amazonaws.services.sqs.model.SendMessageResult
import com.gu.newsletterlistcleanse.db.{BigQueryOperations, DatabaseOperations}
import com.gu.newsletterlistcleanse.models.{CleanseList, NewsletterCutOff}
import com.gu.newsletterlistcleanse.sqs.{AwsSQSSend, SqsMessageParser}
import com.gu.newsletterlistcleanse.sqs.AwsSQSSend.Payload
import io.circe.parser._
import io.circe.syntax._

import scala.concurrent.ExecutionContext.Implicits.global
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

class GetCleanseListLambda {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  val credentialProvider: AWSCredentialsProvider = new NewsletterSQSAWSCredentialProvider()
  val sqsClient: AmazonSQSAsync = AwsSQSSend.buildSqsClient(credentialProvider)
  val s3Client: AmazonS3 = AmazonS3ClientBuilder.standard
    .withCredentials(credentialProvider)
    .withRegion(Regions.EU_WEST_1).build
  val config: NewsletterConfig = NewsletterConfig.load(credentialProvider)
  val databaseOperations: DatabaseOperations = new BigQueryOperations(config.serviceAccount, config.projectId)

  val timeout: Duration = Duration(15, TimeUnit.MINUTES)

  def handler(sqsEvent: SQSEvent, context: Context): Unit = {
    SqsMessageParser.parse[NewsletterCutOff](sqsEvent) match {
      case Right(newsletterCutOffs) =>
        Await.result(process(newsletterCutOffs, context), timeout)
      case Left(parseErrors) =>
        parseErrors.foreach(e =>logger.error(e.getMessage))
    }
  }

  def sendCleanseList(cleanseList: CleanseList): Future[SendMessageResult] = {
    AwsSQSSend.sendMessage(sqsClient, config.cleanseListSqsUrl, Payload(cleanseList.asJson.noSpaces))
  }

  def fetchCampaignCleanseList(campaignCutOff: NewsletterCutOff): List[String] = {
    if (campaignCutOff.newsletterName == Newsletters.guardianTodayUK) {
      databaseOperations.fetchGuardianTodayUKCleanseList(campaignCutOff).map(_.userId)
    } else {
      databaseOperations.fetchCampaignCleanseList(campaignCutOff).map(_.userId)
    }
  }

  def exportCleanseListToS3(cleanseList: CleanseList, env: Env, context: Context): Unit = {
    val exportJson = cleanseList.asJson.toString
    val date = LocalDate.now().toString
    val key =  s"${env.stage}/$date/${cleanseList.newsletterName}.${context.getAwsRequestId}.json"
    s3Client.putObject(config.backupBucketName, key, exportJson)
  }

  def process(campaignCutOffDates: List[NewsletterCutOff], context: Context): Future[List[SendMessageResult]]  = {
    val env = Env()
    logger.info(s"Starting $env")

    val results = for {
      campaignCutOff <- campaignCutOffDates
      userIds = fetchCampaignCleanseList(campaignCutOff)
      cleanseList = CleanseList(
        campaignCutOff.newsletterName,
        userIds
      )
      _ = logger.info(s"Found ${userIds.length} users of ${campaignCutOff.activeListLength} to remove from ${campaignCutOff.newsletterName}")
      _ = exportCleanseListToS3(cleanseList, env, context)

      batchedCleanseList = cleanseList.getCleanseListBatches(5000)
      (batch, index) <- batchedCleanseList.zipWithIndex
    } yield {
      logger.info(s"Sending batch $index of ${batch.newsletterName}")
      sendCleanseList(batch)
    }

    Future.sequence(results)
  }
}

object TestGetCleanseList {
  def main(args: Array[String]): Unit = {
    val json = """{"newsletterName":"Editorial_GuardianTodayUK","cutOffDate":"2020-06-07T11:31:14Z[Europe/London]", "activeListLength": 1000}"""
    val parsedJson = decode[NewsletterCutOff](json).right.get
    val getCleanseListLambda = new GetCleanseListLambda
    Await.result(getCleanseListLambda.process(List(parsedJson)), getCleanseListLambda.timeout)
    getCleanseListLambda.sqsClient.shutdown()

  }
}
