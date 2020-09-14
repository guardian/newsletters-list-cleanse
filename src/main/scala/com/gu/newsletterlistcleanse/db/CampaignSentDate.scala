package com.gu.newsletterlistcleanse.db

import java.time.ZonedDateTime

case class CampaignSentDate(
  campaignId: String,
  campaignName: String,
  timestamp: ZonedDateTime
)
