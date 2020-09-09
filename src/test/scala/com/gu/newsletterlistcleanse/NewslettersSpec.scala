package com.gu.newsletterlistcleanse

import java.time.ZonedDateTime

import com.gu.newsletterlistcleanse.db.{ActiveListLength, CampaignSentDate}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.util.Random

class NewslettersSpec extends AnyFlatSpec with Matchers {

  val newsletters = new Newsletters
  val aDate: ZonedDateTime = ZonedDateTime.parse("2020-01-01T00:00:00Z")
  val aCampaignSentDate: CampaignSentDate = CampaignSentDate(
    campaignId = "1fc37a77-e6ec-4549-a908-f4b7a04a13be",
    campaignName = "Editorial_GuardianTodayUK",
    timestamp = aDate
  )

  val aListLength: List[ActiveListLength] = List(
    ActiveListLength("Editorial_GuardianTodayUK", 100)
  )

  "The Newsletter cut-off date calculation logic" should "ignore an empty list" in {
    newsletters.computeCutOffDates(Nil, aListLength) should be(Nil)
  }

  it should "ignore newsletters if not enough newsletters have been sent yet to start cleansing" in {
    val listOfSentDates = Range(0, 94).toList.map { dateOffset =>
      aCampaignSentDate.copy(timestamp = aCampaignSentDate.timestamp.plusDays(dateOffset))
    }
    listOfSentDates.length should be(94)
    val result = newsletters.computeCutOffDates(listOfSentDates, aListLength)
    result should be(Nil)
  }

  it should "pick the 94th date of the campaignSentDate" in {
    val listOfSentDates = Range(0, 95).toList.map { dateOffset =>
      aCampaignSentDate.copy(timestamp = aCampaignSentDate.timestamp.plusDays(dateOffset))
    }
    val result = newsletters.computeCutOffDates(listOfSentDates, aListLength)
    result.head.cutOffDate should be(aDate)
  }

  it should "ignore a newsletter that hasn't got a cleansing policy yet" in {
    val listOfSentDates = Range(0, 95).toList.map { dateOffset =>
      aCampaignSentDate.copy(
        campaignName = "I_DONT_EXIST",
        timestamp = aCampaignSentDate.timestamp.plusDays(dateOffset)
      )
    }
    val result = newsletters.computeCutOffDates(listOfSentDates, aListLength)
    result should be(Nil)
  }

  it should "pick the 94th date of the campaignSentDate, regardless of how many results are returned by the DB" in {
    val listOfSentDates = Range(0, 1000).toList.map { dateOffset =>
      aCampaignSentDate.copy(timestamp = aCampaignSentDate.timestamp.plusDays(dateOffset))
    }
    val result = newsletters.computeCutOffDates(listOfSentDates, aListLength)
    result.head.cutOffDate should be(aDate.plusDays(999 - 94))
  }

  it should "pick the 94th date of the campaignSentDate, regardless of the order of the rows returned by the DB" in {
    val listOfSentDates = Random.shuffle(Range(0, 1000).toList).map { dateOffset =>
      aCampaignSentDate.copy(timestamp = aCampaignSentDate.timestamp.plusDays(dateOffset))
    }
    val result = newsletters.computeCutOffDates(listOfSentDates, aListLength)
    result.head.cutOffDate should be(aDate.plusDays(999 - 94))
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
    val result = newsletters.computeCutOffDates(listOfSentDates1 ++ listOfSentDates2, aListLength)
    result.head.cutOffDate should be(aDate.plusDays(999 - 94))
    result(1).cutOffDate should be(aDate.plusDays(999 - 61))
  }

}
