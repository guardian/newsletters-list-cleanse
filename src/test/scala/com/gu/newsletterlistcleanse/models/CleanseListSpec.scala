package com.gu.newsletterlistcleanse.models

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.syntax._


class CleanseListSpec extends AnyFlatSpec with Matchers {


  "The CleanseList JSON conversion" should "handle an empty list" in {
    val cleanseList: CleanseList = CleanseList("TestNewsletter", Nil)
    cleanseList.asJson.noSpaces should be("{\"newsletterName\":\"TestNewsletter\",\"userIdList\":[]}")
  }

  it should "handle a single list" in {
    val cleanseList: CleanseList = CleanseList("TestNewsletter", List("a", "b", "c", "d", "e"))
    cleanseList.asJson.noSpaces should be("{\"newsletterName\":\"TestNewsletter\",\"userIdList\":[\"a\",\"b\",\"c\",\"d\",\"e\"]}")
  }
}

