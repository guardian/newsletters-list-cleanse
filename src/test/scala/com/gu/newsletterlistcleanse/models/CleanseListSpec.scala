package com.gu.newsletterlistcleanse.models

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.syntax._


class CleanseListSpec extends AnyFlatSpec with Matchers {

  val shortList = List("a", "b", "c", "d", "e")

  "The CleanseList JSON conversion" should "handle an empty list" in {
    val cleanseList: CleanseList = CleanseList("TestNewsletter", Nil)
    cleanseList.asJson.noSpaces should be("{\"newsletterName\":\"TestNewsletter\",\"userIdList\":[]}")
  }

  it should "handle a single list" in {
    val cleanseList: CleanseList = CleanseList("TestNewsletter", List("a", "b", "c", "d", "e"))
    cleanseList.asJson.noSpaces should be("{\"newsletterName\":\"TestNewsletter\",\"userIdList\":[\"a\",\"b\",\"c\",\"d\",\"e\"]}")
  }

  "The batch split logic" should "ignore an empty list" in {
    val cleanseList: CleanseList = CleanseList("TestNewsletter", Nil)


    cleanseList.getCleanseListBatches(100) should be(Nil)
  }

  it should "return a List of cleanseLists with Lists of users of length `usersPerMessage`" in {
    val cleanseList: CleanseList = CleanseList("TestNewsletter", shortList)

    cleanseList.getCleanseListBatches(2) should be(
      List(
        CleanseList("TestNewsletter", List("a", "b")),
        CleanseList("TestNewsletter", List("c", "d")),
        CleanseList("TestNewsletter", List("e"))
      )
    )

  }
}

