package com.gu.newsletterlistcleanse.db

import scalikejdbc._
import scalikejdbc.athena._

trait Campaigns {
  def fetchCampaignSentDates(campaignNames: List[String], cutOffLength: Int): List[CampaignSentDate]
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
        WHERE rn <= cutOffLength
      """.map(CampaignSentDate.fromRow).list().apply()
    }
  }
}
