package com.gu.newsletterlistcleanse

import com.amazonaws.services.lambda.runtime.Context
import com.gu.newsletterlistcleanse.db.{Campaigns, CampaignsFromDB}
import org.slf4j.{Logger, LoggerFactory}

import scala.beans.BeanProperty


case class GetCleanseListLambdaInput(
  @BeanProperty
  newslettersToProcess: List[String]
)

object GetCleanseListLambda {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)
  val campaigns: Campaigns = new CampaignsFromDB()

  def handler(lambdaInput: GetCleanseListLambdaInput, context: Context): Unit = {
    val env = Env()
    logger.info(s"Starting $env")
    process(lambdaInput)
  }

  def process(lambdaInput: GetCleanseListLambdaInput): Unit = {

    val result = campaigns.fetchCampaignSentDates(lambdaInput.newslettersToProcess)

    logger.info(s"result: ${result}")
  }
}

object TestGetCleanseList {
  def main(args: Array[String]): Unit = {
    GetCleanseListLambda.process(GetCleanseListLambdaInput(List("Editorial_AnimalsFarmed")))
  }
}
