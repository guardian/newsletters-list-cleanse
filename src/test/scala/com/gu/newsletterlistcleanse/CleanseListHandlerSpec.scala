package com.gu.newsletterlistcleanse

import com.gu.newsletterlistcleanse.models.CleanseList
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class CleanseListHandlerSpec extends AnyFlatSpec with Matchers {

  val shortList = List("a", "b", "c", "d", "e")

  "The batch split logic" should "ignore an empty list" in {
    val cleanseList: CleanseList = CleanseList("TestNewsletter", Nil)

    val cleanseListHandler: CleanseListHandler = CleanseListHandler(cleanseList)
    cleanseListHandler.getCleanseListBatches(100) should be(Map())
  }

  it should "return equal lists of maxBatchSize with any spare left over" in {
    val cleanseList: CleanseList = CleanseList("TestNewsletter", shortList)

    val cleanseListHandler: CleanseListHandler = CleanseListHandler(cleanseList)

    cleanseListHandler.getCleanseListBatches(2) should be(
      Map(
        "1" -> CleanseList("TestNewsletter", List("a", "b")),
        "2" -> CleanseList("TestNewsletter", List("c", "d")),
        "3" -> CleanseList("TestNewsletter", List("e"))
      )
    )

  }

  it should "return the full list when the maxBatchSize > list length" in {
    val cleanseList: CleanseList = CleanseList("TestNewsletter", shortList)

    val cleanseListHandler: CleanseListHandler = CleanseListHandler(cleanseList)

    cleanseListHandler.getCleanseListBatches(100) should be(
      Map(
        "1" -> CleanseList("TestNewsletter", List("a", "b", "c", "d", "e"))
      )
    )

  }


}
