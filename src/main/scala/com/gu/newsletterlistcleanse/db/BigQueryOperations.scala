package com.gu.newsletterlistcleanse.db

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.time.{Instant, ZoneId, ZonedDateTime}

import com.gu.newsletterlistcleanse.models.NewsletterCutOff
import com.google.cloud.bigquery.{BigQuery, BigQueryOptions, QueryJobConfiguration, QueryParameterValue}
import com.google.auth.Credentials
import com.google.auth.oauth2.ServiceAccountCredentials

import scala.collection.JavaConverters._

class BigQueryOperations(serviceAccount: String, projectId: String) extends DatabaseOperations {

  private val serviceAccountInputStream = new ByteArrayInputStream(serviceAccount.getBytes(StandardCharsets.UTF_8))

  private val credentials: Credentials = ServiceAccountCredentials
    .fromStream(serviceAccountInputStream)
    .toBuilder
    .setScopes(List("https://www.googleapis.com/auth/bigquery").asJavaCollection)
    .build()

  private val bigQuery: BigQuery = BigQueryOptions
    .newBuilder()
    .setCredentials(credentials)
    .setProjectId(projectId)
    .build()
    .getService

  private def toUTCZonedDateTime(epochMicro: Long): ZonedDateTime = {
    ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMicro / 1000), ZoneId.of("UTC"))
  }

  private def toBigQueryTimestamp(zonedDateTime: ZonedDateTime): Long = zonedDateTime.toInstant.toEpochMilli * 1000

  override def fetchCampaignSentDates(campaignNames: List[String], cutOffLength: Int): List[CampaignSentDate] = {

    val sql = """SELECT campaign_name, campaign_id, timestamp FROM (
                |  SELECT row_number() over(Partition by campaign_id ORDER BY campaign_id, timestamp desc) AS rn, *
                |  FROM (
                |    SELECT
                |      campaign_name,
                |      campaign_id,
                |      timestamp
                |    FROM
                |      `datalake.braze_dispatch`
                |    WHERE campaign_name IN UNNEST(@campaignNames)
                |  )
                |)
                |WHERE rn <= @cutOffLength
                |order by campaign_name, timestamp desc""".stripMargin

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

  override def fetchCampaignActiveListLength(newsletterNames: List[String]): List[ActiveListLength] = {
    val sql = """SELECT newsletter_name,
                 |         count(identity_id) AS listLength
                 |FROM `datalake.braze_newsletter_membership`
                 |WHERE customer_status='active'
                 |        AND newsletter_name IN UNNEST(@newsletterNames)
                 |GROUP BY newsletter_name;""".stripMargin

    val queryConfig = QueryJobConfiguration.newBuilder(sql)
      .addNamedParameter("newsletterNames", QueryParameterValue.array(newsletterNames.toArray, classOf[String]))
      .setUseLegacySql(false)
      .build()
    val results = bigQuery.query(queryConfig)

    results.iterateAll().asScala.toList.map { result =>
      ActiveListLength(
        newsletterName = result.get("newsletter_name").getStringValue,
        listLength = result.get("listLength").getLongValue.toInt
      )
    }
  }
}
