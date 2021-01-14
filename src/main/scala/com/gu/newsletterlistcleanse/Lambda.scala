package com.gu.newsletterlistcleanse

import java.util.concurrent.TimeUnit

import cats.implicits._
import cats.data.EitherT
import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.regions.Regions
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import com.amazonaws.services.sns.{AmazonSNS, AmazonSNSClientBuilder}
import com.gu.newsletterlistcleanse.db.{BigQueryOperations, DatabaseOperations}
import com.gu.newsletterlistcleanse.models.Newsletter
import com.gu.newsletterlistcleanse.services.{BrazeUsersService, CleanseListService, CutOffDatesService, NewslettersApiClient, ReportService}
import org.slf4j.{Logger, LoggerFactory}

import scala.beans.BeanProperty
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

case class GetCutOffDatesLambdaInput(
  @BeanProperty
  var newslettersToProcess: Array[String],
  @BeanProperty
  var dryRun: Boolean
) {
  // set a default constructor so Jackson is able to instantiate the class as a java bean
  def this() = this(
    newslettersToProcess = new Array[String](0),
    dryRun = true
  )
}

class Lambda {
  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  val timeout: Duration = Duration(15, TimeUnit.MINUTES)

  val credentialProvider: AWSCredentialsProvider = new NewsletterSQSAWSCredentialProvider()
  val config: NewsletterConfig = NewsletterConfig.load(credentialProvider)
  val s3Client: AmazonS3 = AmazonS3ClientBuilder.standard
    .withCredentials(credentialProvider)
    .withRegion(Regions.EU_WEST_1)
    .build()
  val snsClient: AmazonSNS = AmazonSNSClientBuilder.standard
    .withCredentials(credentialProvider)
    .withRegion(Regions.EU_WEST_1)
    .build()
  val databaseOperations: DatabaseOperations = new BigQueryOperations(config.serviceAccount, config.projectId)
  val newsletters: NewslettersApiClient = new NewslettersApiClient()

  val cutOffDatesService = new CutOffDatesService(databaseOperations)
  val cleanseListService = new CleanseListService(config, s3Client, databaseOperations)
  val brazeService = new BrazeUsersService(config)
  val reportService = new ReportService(snsClient, config)

  def handler(lambdaInput: GetCutOffDatesLambdaInput, context: Context): Unit = {
    val env = Env()
    val dryRun = config.dryRun || lambdaInput.dryRun
    logger.info(s"Starting $env")
    logger.info(s"DryRun: $dryRun (config says ${config.dryRun} and event says ${lambdaInput.dryRun})")
    val updateResults = for {
      newslettersToProcess <- fetchNewsletters(lambdaInput.newslettersToProcess.toList)
      cutOffDates <- EitherT.fromEither[Future](cutOffDatesService.fetchAndComputeCutOffDates(newslettersToProcess))
      cleanseLists = cleanseListService.fetchCleanseLists(cutOffDates, Option(context), env)
      result <- brazeService.getBrazeResults(cleanseLists, dryRun)
      _ = reportService.sendReport(cleanseLists, env, dryRun)
    } yield result

    Await.result(updateResults.value, timeout) match {
      case Left(error) =>
        logger.error(error)
        throw new RuntimeException("Errors encountered during list cleanse")
      case Right(success) =>
        logger.info(s"Updated ${success.length} batches of users in Braze")
    }
  }

  def fetchNewsletters(newslettersToProcess: List[String]): EitherT[Future, String, List[Newsletter]] = {
    if (newslettersToProcess.nonEmpty) {
      newsletters.fetchNewsletters(newslettersToProcess)
    } else {
      newsletters.fetchNewsletters()
    }
  }
}
