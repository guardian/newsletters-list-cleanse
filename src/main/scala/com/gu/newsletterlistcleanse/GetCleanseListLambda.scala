package com.gu.newsletterlistcleanse

import java.util.concurrent.TimeUnit

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.services.lambda.runtime.events.SQSEvent
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
  val config: NewsletterConfig = NewsletterConfig.load(credentialProvider)
  val databaseOperations: DatabaseOperations = new BigQueryOperations(config.serviceAccount, config.projectId)

  val timeout: Duration = Duration(15, TimeUnit.MINUTES)

  def handler(sqsEvent: SQSEvent): Unit = {
    SqsMessageParser.parse[NewsletterCutOff](sqsEvent) match {
      case Right(newsletterCutOffs) =>
        Await.result(process(newsletterCutOffs), timeout)
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

  def process(campaignCutOffDates: List[NewsletterCutOff]): Future[List[SendMessageResult]]  = {
    val env = Env()
    logger.info(s"Starting $env")

    val results = for {
      campaignCutOff <- campaignCutOffDates
      userIds = fetchCampaignCleanseList(campaignCutOff)
      cleanseList = CleanseList(
        newsletterName = campaignCutOff.newsletterName,
        userIdList = userIds,
        dryRun = campaignCutOff.dryRun
      )

      _ = logger.info(s"Found ${userIds.length} users of ${campaignCutOff.activeListLength} to remove from ${campaignCutOff.newsletterName}")

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
  }
}
