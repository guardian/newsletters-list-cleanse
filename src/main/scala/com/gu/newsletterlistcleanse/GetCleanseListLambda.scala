package com.gu.newsletterlistcleanse

import com.amazonaws.services.lambda.runtime.Context
import com.gu.identity.model.EmailNewsletters
import com.gu.newsletterlistcleanse.db.{Campaigns, CampaignsFromDB}
import org.slf4j.{Logger, LoggerFactory}
import io.circe.syntax._

import scala.beans.BeanProperty


case class GetCleanseListLambdaInput(
  @BeanProperty
  newslettersToProcess: List[String]
)

object GetCleanseListLambda {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)
  val campaigns: Campaigns = new CampaignsFromDB()
  val newsletters: Newsletters = new Newsletters()

  def handler(lambdaInput: GetCleanseListLambdaInput, context: Context): Unit = {
    val env = Env()
    logger.info(s"Starting $env")
    process(lambdaInput)
  }

  def process(lambdaInput: GetCleanseListLambdaInput): Unit = {

    val newslettersToProcess = Option(lambdaInput.newslettersToProcess) // this is set by AWS, so potentially null
      .getOrElse(newsletters.allNewsletters)
    val campaignSentDates = campaigns.fetchCampaignSentDates(newslettersToProcess)
    val cutOffDates = newsletters.computeCutOffDates(campaignSentDates)
    logger.info(s"result: ${cutOffDates.asJson}")
  }
}

object TestGetCleanseList {
  def main(args: Array[String]): Unit = {
    GetCleanseListLambda.process(GetCleanseListLambdaInput(List("Editorial_AnimalsFarmed")))
  }
}
