package com.gu.newsletterlistcleanse.db

import java.time.{ ZoneId, ZonedDateTime }
import java.time.format.DateTimeFormatter

import scalikejdbc.WrappedResultSet

case class CampaignSentDates(
  campaignId: String,
  campaignName: String,
  timestamp: ZonedDateTime)

object CampaignSentDates {

  val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.of("UTC"))

  def fromRow(rs: WrappedResultSet): CampaignSentDates = CampaignSentDates(
    campaignId = rs.string("campaign_id"),
    campaignName = rs.string("campaign_name"),
    timestamp = ZonedDateTime.from(formatter.parse(rs.string("timestamp"))))
}
