package com.gu.newsletterlistcleanse.db

import com.gu.newsletterlistcleanse.models.NewsletterCutOff

trait DatabaseOperations {
  def fetchCampaignSentDates(campaignNames: List[String], cutOffLength: Int): List[CampaignSentDate]

  def fetchCampaignCleanseList(newsletterCutOff: NewsletterCutOff): List[UserID]
}

