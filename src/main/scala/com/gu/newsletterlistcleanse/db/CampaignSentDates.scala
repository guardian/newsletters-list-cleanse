package com.gu.newsletterlistcleanse.db

import java.time.ZonedDateTime

import scalikejdbc.WrappedResultSet

case class CampaignSentDates(
  campaignId: String,
  campaignName: String,
  timestamp: ZonedDateTime)

object CampaignSentDates {
  def fromRow(rs: WrappedResultSet): CampaignSentDates = CampaignSentDates(
    campaignId = rs.string("campaign_id"),
    campaignName = rs.string("campaign_name"),
    timestamp = rs.dateTime("timestamp"))
}
