package com.gu.newsletterlistcleanse

import cats.implicits._
import cats.data.EitherT
import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.regions.Regions
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import com.gu.newsletterlistcleanse.db.{BigQueryOperations, DatabaseOperations}
import com.gu.newsletterlistcleanse.models.Newsletter
import com.gu.newsletterlistcleanse.services.{CutOffDatesService, CleanseListService, NewslettersApiClient}
import org.slf4j.{Logger, LoggerFactory}

import scala.beans.BeanProperty
import scala.concurrent.Future
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

class Lambda {
  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  val credentialProvider: AWSCredentialsProvider = new NewsletterSQSAWSCredentialProvider()
  val config: NewsletterConfig = NewsletterConfig.load(credentialProvider)
  val s3Client: AmazonS3 = AmazonS3ClientBuilder.standard
    .withCredentials(credentialProvider)
    .withRegion(Regions.EU_WEST_1).build
  val databaseOperations: DatabaseOperations = new BigQueryOperations(config.serviceAccount, config.projectId)
  val newsletters: NewslettersApiClient = new NewslettersApiClient()

  val cutOffDatesService = new CutOffDatesService(databaseOperations)
  val cleanseListService = new CleanseListService(config, s3Client, databaseOperations)

  def handler(lambdaInput: GetCutOffDatesLambdaInput, context: Context): Unit = {
    val env = Env()
    logger.info(s"Starting $env")
    for {
      newslettersToProcess <- fetchNewsletters(lambdaInput.newslettersToProcess.toList)
      cutOffDates <- EitherT.fromEither[Future](cutOffDatesService.fetchAndComputeCutOffDates(newslettersToProcess))
      cleanseLists = cleanseListService.process(cutOffDates, Some(context))
    } yield {
      cleanseLists
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
