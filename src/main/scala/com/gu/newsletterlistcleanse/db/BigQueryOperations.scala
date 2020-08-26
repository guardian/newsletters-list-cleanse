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

  private def toBigQueryTimestamp(zonedDateTime: ZonedDateTime): Long = zonedDateTime.toInstant.toEpochMilli * 1000

  override def fetchCampaignSentDates(campaignNames: List[String], cutOffLength: Int): List[CampaignSentDate] = {

    val sql = """SELECT campaign_name, campaign_id, timestamp FROM (
                |  SELECT row_number() over(PARTITION BY campaign_name) AS rn, *
                |  FROM (
                |    SELECT
                |      campaign_name,
                |      campaign_id,
                |      timestamp
                |    FROM
                |      `datalake.braze_dispatch`
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

  override def fetchCampaignCleanseList(newsletterCutOff: NewsletterCutOff): List[UserID] = {

    val sql = """SELECT DISTINCT users.external_id.id AS user_id
                |FROM
                |  `datalake.braze_email_send` as send,
                |  `datalake.braze_users` as users
                |WHERE
                |NOT exists (
                |  SELECT 1
                |  FROM `datalake.braze_email_open` AS open
                |  WHERE
                |    send.user_id = open.user_id
                |    AND open.campaign_name = send.campaign_name
                |    AND open.event_date >= DATE(@formattedDate)
                |) AND exists (
                |  SELECT 1
                |  FROM `datalake.braze_newsletter_membership` AS membership
                |  WHERE
                |  send.identity_id = membership.identity_id
                |  AND send.campaign_name = membership.newsletter_name
                |  AND membership.customer_status = 'active'
                |)
                |AND send.campaign_name = @campaignName
                |AND send.identity_id = users.identity_id
                |AND send.event_date >= DATE(@formattedDate)""".stripMargin

    val queryConfig = QueryJobConfiguration.newBuilder(sql)
      .addNamedParameter("campaignName", QueryParameterValue.string(newsletterCutOff.newsletterName))
      .addNamedParameter("formattedDate", QueryParameterValue.timestamp(toBigQueryTimestamp(newsletterCutOff.cutOffDate)))
      .setUseLegacySql(false)
      .build()

    val results = bigQuery.query(queryConfig)

    results.iterateAll().asScala.toList.map { result =>
      UserID(result.get("user_id").getStringValue)
    }
  }
}
