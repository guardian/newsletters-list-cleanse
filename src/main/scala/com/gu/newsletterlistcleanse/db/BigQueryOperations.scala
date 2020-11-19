package com.gu.newsletterlistcleanse.db

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.time.{Instant, ZoneId, ZonedDateTime}

import com.gu.newsletterlistcleanse.models.NewsletterCutOff
import com.google.cloud.bigquery.{BigQuery, BigQueryOptions, FieldValueList, QueryJobConfiguration, QueryParameterValue}
import com.google.auth.Credentials
import com.google.auth.oauth2.ServiceAccountCredentials
import com.gu.newsletterlistcleanse.Newsletters

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

  private def selectData[A](sql: String, params: Map[String, QueryParameterValue])(transform: FieldValueList => A): List[A] = {
    val queryConfig = QueryJobConfiguration.newBuilder(sql)
      .setNamedParameters(params.asJava)
      .setUseLegacySql(false)
      .build()

    val results = bigQuery.query(queryConfig)

    results.iterateAll().asScala.toList.map(transform)
  }

  override def fetchCampaignSentDates(campaignNames: List[String], cutOffLength: Int): List[CampaignSentDate] = {

    val sql = """SELECT campaign_name, campaign_id, timestamp FROM (
                |  SELECT row_number() over(PARTITION BY campaign_id ORDER BY campaign_id, timestamp desc) AS rn, *
                |  FROM (
                |    SELECT
                |      campaign_name,
                |      campaign_id,
                |      timestamp
                |    FROM
                |      `datalake.braze_dispatch`
                |    WHERE campaign_name IN UNNEST(@campaignNames)
                |    AND DATE(timestamp) < DATE_SUB(CURRENT_DATE(), INTERVAL 7 DAY)
                |  )
                |)
                |WHERE rn <= @cutOffLength
                |order by campaign_name, timestamp desc""".stripMargin

    val params = Map(
      "campaignNames" -> QueryParameterValue.array(campaignNames.toArray, classOf[String]),
      "cutOffLength" -> QueryParameterValue.int64(cutOffLength: Integer)
    )

    selectData(sql, params) { result =>
      CampaignSentDate(
        campaignId = result.get("campaign_id").getStringValue,
        campaignName = result.get("campaign_name").getStringValue,
        timestamp = toUTCZonedDateTime(result.get("timestamp").getTimestampValue)
      )
    }
  }

  override def fetchGuardianTodayUKSentDates(cutOffLength: Int): List[CampaignSentDate] = {
    val sql = """SELECT @guardianToday as campaign_name, campaign_id, timestamp FROM (
                |  SELECT row_number() over(ORDER BY timestamp desc) AS rn, *
                |  FROM (
                |    SELECT
                |      campaign_name,
                |      campaign_id,
                |      timestamp
                |    FROM
                |      `datalake.braze_dispatch`
                |    WHERE campaign_name IN UNNEST(@campaignNames)
                |    AND DATE(timestamp) < DATE_SUB(CURRENT_DATE(), INTERVAL 7 DAY)
                |  )
                |)
                |WHERE rn <= @cutOffLength
                |order by timestamp desc;""".stripMargin

    val params = Map(
      "guardianToday" -> QueryParameterValue.string(Newsletters.guardianTodayUK),
      "campaignNames" -> QueryParameterValue.array(Newsletters.guardianTodayUKCampaigns.toArray, classOf[String]),
      "cutOffLength" -> QueryParameterValue.int64(cutOffLength: Integer)
    )

    selectData(sql, params) { result =>
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
                |  AND DATE(membership.joined_timestamp) < DATE(@formattedDate)
                |  AND membership.customer_status = 'active'
                |)
                |AND send.campaign_name = @campaignName
                |AND send.identity_id = users.identity_id
                |AND send.event_date >= DATE(@formattedDate)""".stripMargin

    val params = Map(
      "campaignName" -> QueryParameterValue.string(newsletterCutOff.newsletterName),
      "formattedDate" -> QueryParameterValue.timestamp(toBigQueryTimestamp(newsletterCutOff.cutOffDate))
    )

    selectData(sql, params) { result =>
      UserID(result.get("user_id").getStringValue)
    }
  }

  override def fetchGuardianTodayUKCleanseList(newsletterCutOff: NewsletterCutOff): List[UserID] = {
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
                |    AND open.campaign_name in UNNEST(@campaignNames)
                |    AND open.event_date >= DATE(@formattedDate)
                |) AND exists (
                |  SELECT 1
                |  FROM `datalake.braze_newsletter_membership` AS membership
                |  WHERE
                |  send.identity_id = membership.identity_id
                |  AND membership.newsletter_name = @campaignName
                |  AND DATE(membership.joined_timestamp) < DATE(@formattedDate)
                |  AND membership.customer_status = 'active'
                |)
                |AND send.campaign_name in UNNEST(@campaignNames)
                |AND send.identity_id = users.identity_id
                |AND send.event_date >= DATE(@formattedDate)""".stripMargin

    val params = Map(
      "campaignName" -> QueryParameterValue.string(newsletterCutOff.newsletterName),
      "campaignNames" -> QueryParameterValue.array(Newsletters.guardianTodayUKCampaigns.toArray, classOf[String]),
      "formattedDate" -> QueryParameterValue.timestamp(toBigQueryTimestamp(newsletterCutOff.cutOffDate))
    )

    selectData(sql, params) { result =>
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

    val params = Map("newsletterNames" -> QueryParameterValue.array(newsletterNames.toArray, classOf[String]))

    selectData(sql, params) { result =>
      ActiveListLength(
        newsletterName = result.get("newsletter_name").getStringValue,
        listLength = result.get("listLength").getLongValue.toInt
      )
    }
  }
}
