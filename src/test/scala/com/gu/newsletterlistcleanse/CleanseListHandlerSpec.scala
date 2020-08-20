package com.gu.newsletterlistcleanse

import com.gu.newsletterlistcleanse.models.CleanseList
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class CleanseListHandlerSpec extends AnyFlatSpec with Matchers {

  val shortList = List("a", "b", "c", "d", "e")

  "The batch split logic" should "ignore an empty list" in {
    val cleanseList: CleanseList = CleanseList("TestNewsletter", Nil)

    val cleanseListHandler: CleanseListHandler = CleanseListHandler(cleanseList)
    cleanseListHandler.getCleanseListBatches(100) should be(Nil)
  }

  it should "return a List of cleanseLists with Lists of users of length `usersPerMessage`" in {
    val cleanseList: CleanseList = CleanseList("TestNewsletter", shortList)

    val cleanseListHandler: CleanseListHandler = CleanseListHandler(cleanseList)

    cleanseListHandler.getCleanseListBatches(2) should be(
      List(
        CleanseList("TestNewsletter", List("a", "b")),
        CleanseList("TestNewsletter", List("c", "d")),
        CleanseList("TestNewsletter", List("e"))
      )
    )

  }

}
