package com.gu.newsletterlistcleanse

import java.time.ZonedDateTime

import com.gu.identity.model.EmailNewsletters
import com.gu.newsletterlistcleanse.db.CampaignSentDate
import com.gu.newsletterlistcleanse.models.NewsletterCutOff

class Newsletters {
  def allNewsletters: List[String] = {
    EmailNewsletters.allNewsletters.map { newsletter =>
      val attr = newsletter.brazeSubscribeAttributeName
      val newsletterName = Newsletters.attributeToNewsletterMapping(attr)
      newsletterName
    }
  }

  private val reverseChrono: Ordering[ZonedDateTime] = (x: ZonedDateTime, y: ZonedDateTime) => y.compareTo(x)

  def computeCutOffDates(campaignSentDates: List[CampaignSentDate]): List[NewsletterCutOff] = {

    def extractCutOffBasedOnCampaign(campaignName: String, sentDates: List[CampaignSentDate]): Option[NewsletterCutOff] = for {
      unOpenCount <- Newsletters.cleansingPolicy.get(campaignName)
      cutOff <- sentDates
        .sortBy(_.timestamp)(reverseChrono)
        .drop(unOpenCount)
        .headOption
        .map(send => NewsletterCutOff(campaignName, send.timestamp))
    } yield cutOff

    campaignSentDates.groupBy(_.campaignName)
      .toList
      .flatMap { case (campaignName, sentDates) => extractCutOffBasedOnCampaign(campaignName, sentDates) }
  }
}

object Newsletters {

  val cleansingPolicy: Map[String, Int] = Map(
    "Editorial_GuardianTodayUK" -> 94,
    "Editorial_GuardianTodayUS" -> 94,
    "Editorial_GuardianTodayAUS" -> 94,
    "Editorial_MorningBriefingUK" -> 94,
    "Editorial_USMorningBriefing" -> 61,
    "Editorial_MorningMailAUS" -> 61,
    //"Editorial_CoronavirusAustraliaAtAGlance" -> 94, => probably too recent to require cleaning
    "Editorial_GlobalDispatch" -> 13,
    "Editorial_TheUSPoliticsMinute" -> 37,
    "Editorial_AustralianPolitics" -> 61,
    "Editorial_BusinessToday" -> 61,
    "Editorial_GreenLight" -> 13,
    "Editorial_LabNotes" -> 13,
    "Editorial_GuardianDocumentaries" -> 7,
    "Editorial_TheLongRead" -> 13,
    "Editorial_AnimalsFarmed" -> 7,
    "Editorial_GunsAndLiesInAmerica" -> 7,
    "Editorial_TheUpside" -> 13,
    "Editorial_ThisLandIsYourLand" -> 7,
    "Editorial_TheRecap" -> 13,
    "Editorial_TheFiver" -> 61,
    "Editorial_TheBreakdown" -> 13,
    "Editorial_TheSpin" -> 13,
    "Editorial_GuardianAustraliaSports" -> 94,
    "Editorial_SleeveNotes" -> 13,
    "Editorial_FilmToday" -> 61,
    "Editorial_Bookmarks" -> 13,
    "Editorial_ArtWeekly" -> 13,
    "Editorial_HearHere" -> 13,
    "Editorial_TheFlyer" -> 13,
    "Editorial_FashionStatement" -> 13,
    "Editorial_TheGuideStayingIn" -> 13,
    "Editorial_SavedForLater" -> 13,
    "Editorial_WordOfMouth" -> 13,
    "Editorial_BestOfGuardianOpinionUK" -> 61,
    "Editorial_ThisIsEurope" -> 13,
    "Editorial_BestOfGuardianOpinionUS" -> 61,
    "Editorial_TheWeekInPatriarchy" -> 13,
    "Editorial_BestOfGuardianOpinionAUS" -> 61,
    "Editorial_FirstDogOnTheMoon" -> 37,
    "Editorial_BusinessView" -> 13,
    "Editorial_GuardianStudentsNetwork" -> 7,
    "Editorial_GuardianUniversities" -> 13,
    "Editorial_SocietyWeekly" -> 13,
    "Editorial_TeacherNetwork" -> 13,
    // "Editorial_WeekendPapers" -> 94, => Marketing
    // "Editorial_OFM" -> 94, => Marketing
    // "MK_FrontPage" -> 94, => Marketing
  )

  val attributeToNewsletterMapping: Map[String, String] = Map(
    "TodayUk_Subscribe_Email" -> "Editorial_GuardianTodayUK",
    "TodayUs_Subscribe_Email" -> "Editorial_GuardianTodayUS",
    "TodayAus_Subscribe_Email" -> "Editorial_GuardianTodayAUS",
    "MorningBriefingUk_Subscribe_Email" -> "Editorial_MorningBriefingUK",
    "MorningBriefingUs_Subscribe_Email" -> "Editorial_USMorningBriefing",
    "MorningMailAus_Subscribe_Email" -> "Editorial_MorningMailAUS",
    "CoronavirusAustraliaAtAGlance_Subscribe_Email" -> "Editorial_CoronavirusAustraliaAtAGlance",
    "GlobalDispatch_Subscribe_Email" -> "Editorial_GlobalDispatch",
    "TheUsPoliticsMinute_Subscribe_Email" -> "Editorial_TheUSPoliticsMinute",
    "AustralianPolitics_Subscribe_Email" -> "Editorial_AustralianPolitics",
    "BusinessToday_Subscribe_Email" -> "Editorial_BusinessToday",
    "GreenLight_Subscribe_Email" -> "Editorial_GreenLight",
    "LabNotes_Subscribe_Email" -> "Editorial_LabNotes",
    "GuardianDocumentaries_Subscribe_Email" -> "Editorial_GuardianDocumentaries",
    "TheLongRead_Subscribe_Email" -> "Editorial_TheLongRead",
    "AnimalsFarmed_Subscribe_Email" -> "Editorial_AnimalsFarmed",
    "GunsAndLiesInAmerica_Subscribe_Email" -> "Editorial_GunsAndLiesInAmerica",
    "TheUpside_Subscribe_Email" -> "Editorial_TheUpside",
    "ThisLandIsYourLand_Subscribe_Email" -> "Editorial_ThisLandIsYourLand",
    "TheRecap_Subscribe_Email" -> "Editorial_TheRecap",
    "TheFiver_Subscribe_Email" -> "Editorial_TheFiver",
    "TheBreakdown_Subscribe_Email" -> "Editorial_TheBreakdown",
    "TheSpin_Subscribe_Email" -> "Editorial_TheSpin",
    "GuardianAustraliaSports_Subscribe_Email" -> "Editorial_GuardianAustraliaSports",
    "SleeveNotes_Subscribe_Email" -> "Editorial_SleeveNotes",
    "FilmToday_Subscribe_Email" -> "Editorial_FilmToday",
    "Bookmarks_Subscribe_Email" -> "Editorial_Bookmarks",
    "ArtWeekly_Subscribe_Email" -> "Editorial_ArtWeekly",
    "HearHere_Subscribe_Email" -> "Editorial_HearHere",
    "TheFlyer_Subscribe_Email" -> "Editorial_TheFlyer",
    "FashionStatement_Subscribe_Email" -> "Editorial_FashionStatement",
    "TheGuideStayingIn_Subscribe_Email" -> "Editorial_TheGuideStayingIn",
    "SavedForLater_Subscribe_Email" -> "Editorial_SavedForLater",
    "WordOfMouth_Subscribe_Email" -> "Editorial_WordOfMouth",
    "BestOfGuardianOpinionUK_Subscribe_Email" -> "Editorial_BestOfGuardianOpinionUK",
    "ThisIsEurope_Subscribe_Email" -> "Editorial_ThisIsEurope",
    "BestOfGuardianOpinionUS_Subscribe_Email" -> "Editorial_BestOfGuardianOpinionUS",
    "TheWeekInPatriarchy_Subscribe_Email" -> "Editorial_TheWeekInPatriarchy",
    "BestOfGuardianOpinionAus_Subscribe_Email" -> "Editorial_BestOfGuardianOpinionAUS",
    "FirstDogOnTheMoon_Subscribe_Email" -> "Editorial_FirstDogOnTheMoon",
    "BusinessView_Subscribe_Email" -> "Editorial_BusinessView",
    "GuardianStudentsNetwork_Subscribe_Email" -> "Editorial_GuardianStudentsNetwork",
    "GuardianUniversities_Subscribe_Email" -> "Editorial_GuardianUniversities",
    "SocietyWeekly_Subscribe_Email" -> "Editorial_SocietyWeekly",
    "TeacherNetwork_Subscribe_Email" -> "Editorial_TeacherNetwork",
    "TheWeekendPapers_Subscribe_Email" -> "Editorial_WeekendPapers",
    "TheObserverFoodMonthly_Subscribe_Email" -> "Editorial_OFM",
    "FrontPageNewsletter_Subscribe_Email" -> "MK_FrontPage"
  )

  val newsletterToAttribute: Map[String, String] = attributeToNewsletterMapping.map(_.swap)
}
