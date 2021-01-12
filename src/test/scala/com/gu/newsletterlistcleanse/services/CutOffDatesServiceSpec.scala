package com.gu.newsletterlistcleanse.services

import java.time.ZonedDateTime

import com.gu.newsletterlistcleanse.db.{ActiveListLength, CampaignSentDate, DatabaseOperations, UserID}
import com.gu.newsletterlistcleanse.models.{BrazeData, NewsletterCutOff}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.util.Random

class CutOffDatesServiceSpec extends AnyFlatSpec with Matchers {

  val cutOffDatesService = new CutOffDatesService(new DatabaseOperations {
    override def fetchCampaignSentDates(campaignNames: List[String], cutOffLength: Int): List[CampaignSentDate] = ???
    override def fetchGuardianTodayUKSentDates(cutOffLength: Int): List[CampaignSentDate] = ???
    override def fetchCampaignCleanseList(newsletterCutOff: NewsletterCutOff): List[UserID] = ???
    override def fetchGuardianTodayUKCleanseList(newsletterCutOff: NewsletterCutOff): List[UserID] = ???
    override def fetchCampaignActiveListLength(newsletterNames: List[String]): List[ActiveListLength] = ???
  })

  val aDate: ZonedDateTime = ZonedDateTime.parse("2020-01-01T00:00:00Z")
  val testBrazeData: BrazeData = BrazeData("test_attribute_name", "test_event_prefix")
  val aCampaignSentDate: CampaignSentDate = CampaignSentDate(
    campaignId = "1fc37a77-e6ec-4549-a908-f4b7a04a13be",
    campaignName = "Editorial_GuardianTodayUK",
    timestamp = aDate
  )

  val aListLength: List[ActiveListLength] = List(
    ActiveListLength("Editorial_GuardianTodayUK", 100)
  )

  "The Newsletter cut-off date calculation logic" should "ignore an empty list" in {
    cutOffDatesService.computeCutOffDates(Nil, aListLength) should be(Nil)
  }

  it should "ignore newsletters if not enough newsletters have been sent yet to start cleansing" in {
    val listOfSentDates = Range(0, 93).toList.map { dateOffset =>
      aCampaignSentDate.copy(timestamp = aCampaignSentDate.timestamp.plusDays(dateOffset))
    }
    listOfSentDates.length should be(93)
    val result = cutOffDatesService.computeCutOffDates(listOfSentDates, aListLength)
    result should be(Nil)
  }

  it should "pick the 94th date of the campaignSentDate" in {
    val listOfSentDates = Range(0, 94).toList.map { dateOffset =>
      aCampaignSentDate.copy(timestamp = aCampaignSentDate.timestamp.plusDays(dateOffset))
    }
    val result = cutOffDatesService.computeCutOffDates(listOfSentDates, aListLength)
    result.head.cutOffDate should be(aDate)
  }

  it should "ignore a newsletter that hasn't got a cleansing policy yet" in {
    val listOfSentDates = Range(0, 94).toList.map { dateOffset =>
      aCampaignSentDate.copy(
        campaignName = "I_DONT_EXIST",
        timestamp = aCampaignSentDate.timestamp.plusDays(dateOffset)
      )
    }
    val result = cutOffDatesService.computeCutOffDates(listOfSentDates, aListLength)
    result should be(Nil)
  }

  it should "pick the 94th date of the campaignSentDate, regardless of how many results are returned by the DB" in {
    val listOfSentDates = Range(0, 1000).toList.map { dateOffset =>
      aCampaignSentDate.copy(timestamp = aCampaignSentDate.timestamp.plusDays(dateOffset))
    }
    val result = cutOffDatesService.computeCutOffDates(listOfSentDates, aListLength)
    result.head.cutOffDate should be(aDate.plusDays(1000 - 94))
  }

  it should "pick the 94th date of the campaignSentDate, regardless of the order of the rows returned by the DB" in {
    val listOfSentDates = Random.shuffle(Range(0, 1000).toList).map { dateOffset =>
      aCampaignSentDate.copy(timestamp = aCampaignSentDate.timestamp.plusDays(dateOffset))
    }
    val result = cutOffDatesService.computeCutOffDates(listOfSentDates, aListLength)
    result.head.cutOffDate should be(aDate.plusDays(1000 - 94))
  }

  it should "compute no matter how many campaigns are being sent" in {
    val listOfSentDates1 = Range(0, 1000).toList.map { dateOffset =>
      aCampaignSentDate.copy(timestamp = aCampaignSentDate.timestamp.plusDays(dateOffset))
    }
    val listOfSentDates2 = Range(0, 1000).toList.map { dateOffset =>
      aCampaignSentDate.copy(
        campaignName = "Editorial_USMorningBriefing",
        timestamp = aCampaignSentDate.timestamp.plusDays(dateOffset)
      )
    }
    val result = cutOffDatesService.computeCutOffDates(listOfSentDates1 ++ listOfSentDates2, aListLength)
    result.head.cutOffDate should be(aDate.plusDays(1000 - 94))
    result(1).cutOffDate should be(aDate.plusDays(1000 - 61))
  }
}
