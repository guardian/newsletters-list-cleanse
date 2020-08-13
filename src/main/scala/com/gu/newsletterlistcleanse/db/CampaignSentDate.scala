package com.gu.newsletterlistcleanse.db

import java.time.{ ZoneId, ZonedDateTime }
import java.time.format.DateTimeFormatter

import scalikejdbc.WrappedResultSet

case class CampaignSentDate(
  campaignId: String,
  campaignName: String,
  timestamp: ZonedDateTime
)

object CampaignSentDate {

  val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.of("UTC"))

  def fromRow(rs: WrappedResultSet): CampaignSentDate = CampaignSentDate(
    campaignId = rs.string("campaign_id"),
    campaignName = rs.string("campaign_name"),
    timestamp = ZonedDateTime.from(formatter.parse(rs.string("timestamp"))))
}
