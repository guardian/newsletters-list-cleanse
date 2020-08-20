package com.gu.newsletterlistcleanse

import com.gu.newsletterlistcleanse.models.CleanseList
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class CleanseListHandlerSpec extends AnyFlatSpec with Matchers {

  val shortList = List("a", "b", "c", "d", "e")

  "The batch split logic" should "ignore an empty list" in {
    val cleanseList: CleanseList = CleanseList("TestNewsletter", Nil)

    val cleanseListHandler: CleanseListHandler = CleanseListHandler(cleanseList)
    cleanseListHandler.getCleanseListBatches(100, 2) should be(List())
  }

  it should "return a List of Maps with `messagesPerBatch` fields per Map and cleanseLists with Lists of length `usersPerMessage`" in {
    val cleanseList: CleanseList = CleanseList("TestNewsletter", shortList)

    val cleanseListHandler: CleanseListHandler = CleanseListHandler(cleanseList)

    cleanseListHandler.getCleanseListBatches(2, 2) should be(
      List(Map(
        "1" -> CleanseList("TestNewsletter", List("a", "b")),
        "2" -> CleanseList("TestNewsletter", List("c", "d"))),
        Map("3" -> CleanseList("TestNewsletter", List("e")))
      )
    )

  }

  it should "return the full list when the maxBatchSize > list length" in {
    val cleanseList: CleanseList = CleanseList("TestNewsletter", shortList)

    val cleanseListHandler: CleanseListHandler = CleanseListHandler(cleanseList)

    cleanseListHandler.getCleanseListBatches(100, 10) should be(
      List(Map(
        "1" -> CleanseList("TestNewsletter", List("a", "b", "c", "d", "e"))
      ))
    )

  }
}
