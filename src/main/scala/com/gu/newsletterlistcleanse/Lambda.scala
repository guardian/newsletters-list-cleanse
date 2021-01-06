package com.gu.newsletterlistcleanse

import cats.data.EitherT
import com.amazonaws.auth.AWSCredentialsProvider
import com.gu.newsletterlistcleanse.db.{BigQueryOperations, DatabaseOperations}
import com.gu.newsletterlistcleanse.models.Newsletter
import com.gu.newsletterlistcleanse.services.{CutOffDatesService, NewslettersApiClient}
import org.slf4j.{Logger, LoggerFactory}

import scala.beans.BeanProperty
import scala.concurrent.Future

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
  val databaseOperations: DatabaseOperations = new BigQueryOperations(config.serviceAccount, config.projectId)
  val newsletters: NewslettersApiClient = new NewslettersApiClient()

  val cutOffDatesStep = new CutOffDatesService(databaseOperations)

  def handler(): Unit = {
    val env = Env()
    logger.info(s"Starting $env")
  }

  def fetchNewsletters(newslettersToProcess: List[String]): EitherT[Future, String, List[Newsletter]] = {
    if (newslettersToProcess.nonEmpty) {
      newsletters.fetchNewsletters(newslettersToProcess)
    } else {
      newsletters.fetchNewsletters()
    }
  }
}
