package com.gu.newsletterlistcleanse.db

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import com.gu.newsletterlistcleanse.models.NewsletterCutOff
import scalikejdbc._
import scalikejdbc.athena._

trait Campaigns {
  def fetchCampaignSentDates(campaignNames: List[String], cutOffLength: Int): List[CampaignSentDate]

  def fetchCampaignCleanseList(newsletterCutOff: NewsletterCutOff): List[UserID]
}

class CampaignsFromDB extends Campaigns {
  override def fetchCampaignSentDates(campaignNames: List[String], cutOffLength: Int): List[CampaignSentDate] = {
    DB.athena { implicit session =>
      sql"""
        SELECT campaign_name, campaign_id, timestamp FROM (
          SELECT row_number() over(PARTITION BY campaign_name) AS rn, *
          FROM (
            SELECT
              campaign_name,
              campaign_id,
              timestamp
            FROM
              "clean"."braze_dispatch"
            WHERE campaign_name IN ($campaignNames)
            ORDER BY timestamp DESC
          )
        )
        WHERE rn <= $cutOffLength
      """.map(CampaignSentDate.fromRow).list().apply()
    }
  }

  override def fetchCampaignCleanseList(newsletterCutOff: NewsletterCutOff): List[UserID] = {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val formattedDate = newsletterCutOff.cutOffDate.format(formatter)
    val campaignName = newsletterCutOff.newsletterName
    DB.athena { implicit session =>
      sql"""
        SELECT DISTINCT users.external_id.id AS user_id
        FROM
          "clean"."braze_email_send" as send,
          "clean"."braze_users" as users
        WHERE
        NOT exists (
          SELECT 1
          FROM "clean"."braze_email_open" AS open
          WHERE
            send.user_id = open.user_id
            AND open.campaign_name = send.campaign_name
            AND open.event_date >= DATE($formattedDate)
        ) AND exists (
          SELECT 1
          FROM "clean"."braze_newsletter_membership" AS membership
          WHERE
          send.identity_id = membership.identity_id
          AND send.campaign_name = membership.newsletter_name
          AND membership.customer_status = 'active'
        )
        AND send.campaign_name = $campaignName
        AND send.identity_id = users.identity_id
        AND send.event_date >= DATE($formattedDate)
        """.map(UserID.fromRow).list().apply()
    }
  }
}
