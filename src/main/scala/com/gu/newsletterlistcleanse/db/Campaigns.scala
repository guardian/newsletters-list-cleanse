package com.gu.newsletterlistcleanse.db

import scalikejdbc._
import scalikejdbc.athena._

trait Campaigns {
  def fetchCampaignSentDates(campaignNames: List[String]): List[CampaignSentDates]
}

class CampaignsFromDB extends Campaigns {
  override def fetchCampaignSentDates(campaignNames: List[String]): List[CampaignSentDates] = {
    DB.athena { implicit session =>
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
            where campaign_name in ($campaignNames)
            order by timestamp desc
          )
        )
        WHERE rn <= 94
      """.map(CampaignSentDates.fromRow).list().apply()
    }
  }
}
