package com.gu.newsletterlistcleanse.db

import com.gu.newsletterlistcleanse.models.NewsletterCutOff

trait DatabaseOperations {
  def fetchCampaignSentDates(campaignNames: List[String], cutOffLength: Int): List[CampaignSentDate]

  def fetchGuardianTodayUKSentDates(cutOffLength: Int): List[CampaignSentDate]

  def fetchCampaignCleanseList(newsletterCutOff: NewsletterCutOff): List[UserID]

  def fetchCampaignActiveListLength(newsletterNames: List[String]): List[ActiveListLength]
}


