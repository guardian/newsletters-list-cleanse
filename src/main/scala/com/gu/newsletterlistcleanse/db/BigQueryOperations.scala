package com.gu.newsletterlistcleanse.db

import java.io.InputStream
import java.time.{Instant, ZoneId, ZonedDateTime}

import com.gu.newsletterlistcleanse.models.NewsletterCutOff
import com.google.cloud.bigquery.{BigQuery, BigQueryOptions, QueryJobConfiguration, QueryParameterValue}
import com.google.auth.Credentials
import com.google.auth.oauth2.ServiceAccountCredentials

import scala.collection.JavaConverters._

class BigQueryOperations(googleCredentials: InputStream) extends DatabaseOperations {

  val credentials: Credentials = ServiceAccountCredentials
    .fromStream(googleCredentials)
    .toBuilder
    .setScopes(List("https://www.googleapis.com/auth/bigquery").asJavaCollection)
    .build()

  val bigQuery: BigQuery = BigQueryOptions
    .newBuilder()
    .setCredentials(credentials)
    .setProjectId("datatech-platform-code")
    .build()
    .getService

  private def toUTCZonedDateTime(epochMicro: Long): ZonedDateTime = {
    ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMicro / 1000), ZoneId.of("UTC"))
  }

  override def fetchCampaignSentDates(campaignNames: List[String], cutOffLength: Int): List[CampaignSentDate] = {

    val sql = """SELECT campaign_name, campaign_id, timestamp FROM (
                |  SELECT row_number() over(PARTITION BY campaign_name) AS rn, *
                |  FROM (
                |    SELECT
                |      campaign_name,
                |      campaign_id,
                |      timestamp
                |    FROM
                |      `clean.braze_dispatch`
                |    WHERE campaign_name IN UNNEST(@campaignNames)
                |    ORDER BY timestamp DESC
                |  )
                |)
                |WHERE rn <= @cutOffLength""".stripMargin

    val queryConfig = QueryJobConfiguration.newBuilder(sql)
      .addNamedParameter("campaignNames", QueryParameterValue.array(campaignNames.toArray, classOf[String]))
      .addNamedParameter("cutOffLength", QueryParameterValue.int64(94L))
      .setUseLegacySql(false)
      .build()

    val results = bigQuery.query(queryConfig)

    results.iterateAll().asScala.toList.map { result =>
      CampaignSentDate(
        campaignId = result.get("campaign_id").getStringValue,
        campaignName = result.get("campaign_name").getStringValue,
        timestamp = toUTCZonedDateTime(result.get("timestamp").getTimestampValue)
      )
    }
  }

  override def fetchCampaignCleanseList(newsletterCutOff: NewsletterCutOff): List[UserID] = ???
}
