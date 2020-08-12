package com.gu.newsletterlistcleanse

import com.amazonaws.services.lambda.runtime.Context
import com.gu.newsletterlistcleanse.db.{ CampaignSentDates, DataLake }
import org.slf4j.{ Logger, LoggerFactory }
import scalikejdbc._

/**
 * This is compatible with aws' lambda JSON to POJO conversion.
 * You can test your lambda by sending it the following payload:
 * {"name": "Bob"}
 */
class GetCleanseListLambdaInput() {
  var name: String = _
  def getName(): String = name
  def setName(theName: String): Unit = name = theName
}

object GetCleanseListLambda {

  DataLake.init()

  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  def handler(lambdaInput: GetCleanseListLambdaInput, context: Context): Unit = {
    val env = Env()
    logger.info(s"Starting $env")
    logger.info(process(lambdaInput.name, env))
  }

  def process(name: String, env: Env): String = {

    val result = DB.readOnly { implicit session =>
      sql"""
        SELECT campaign_name, campaign_id, timestamp FROM (
          SELECT row_number() over(partition by campaign_name) AS rn, *
          FROM (
            SELECT
              campaign_name,
              campaign_id,
              timestamp
            FROM
              "clean"."braze_dispatch"
            where campaign_name in ('Editorial_AnimalsFarmed')
            order by timestamp desc
          )
        )
        WHERE rn <= 94
      """.map(CampaignSentDates.fromRow).list().apply()
    }

    logger.info(s"result: ${result}")

    s"Hello $name! (from ${env.app} in ${env.stack})\n"
  }
}

object TestGetCleanseList {
  def main(args: Array[String]): Unit = {
    println(GetCleanseListLambda.process(args.headOption.getOrElse("Alex"), Env()))
  }
}
