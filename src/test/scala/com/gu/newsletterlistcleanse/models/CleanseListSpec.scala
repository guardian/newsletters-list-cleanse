package com.gu.newsletterlistcleanse.models

import com.gu.newsletterlistcleanse.models
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.syntax._
import io.circe.parser.decode


class CleanseListSpec extends AnyFlatSpec with Matchers {

  val shortList = List("a", "b", "c", "d", "e")

  val testBrazeData: BrazeData = BrazeData("testAttribute", "testEventName")

  "The CleanseList JSON conversion" should "handle an empty list" in {
    val cleanseList: CleanseList = CleanseList("TestNewsletter", Nil, testBrazeData)
    cleanseList.asJson.noSpaces should be("{\"newsletterName\":\"TestNewsletter\",\"userIdList\":[]," +
      "\"brazeData\":{\"brazeSubscribeAttributeName\":\"testAttribute\",\"brazeSubscribeEventNamePrefix\":\"testEventName\"}}")
  }

  it should "handle a single list" in {
    val cleanseList: CleanseList = CleanseList("TestNewsletter", List("a", "b", "c", "d", "e"), testBrazeData)
    cleanseList.asJson.noSpaces should be("{\"newsletterName\":\"TestNewsletter\",\"userIdList\":[\"a\",\"b\",\"c\",\"d\",\"e\"]," +
      "\"brazeData\":{\"brazeSubscribeAttributeName\":\"testAttribute\",\"brazeSubscribeEventNamePrefix\":\"testEventName\"}}")
  }

  it should "decode an empty list correctly" in {
    val jsonString: String = "{\"newsletterName\":\"TestNewsletter\",\"userIdList\":[]," +
      "\"brazeData\":{\"brazeSubscribeAttributeName\":\"testAttribute\",\"brazeSubscribeEventNamePrefix\":\"testEventName\"}}"
    val cleanseList: CleanseList = CleanseList("TestNewsletter", Nil, testBrazeData)
    decode[CleanseList](jsonString) should be(Right(cleanseList))
  }

  it should "decode a single list correctly" in {
    val jsonString: String = "{\"newsletterName\":\"TestNewsletter\",\"userIdList\":[\"a\",\"b\",\"c\",\"d\",\"e\"]," +
      "\"brazeData\":{\"brazeSubscribeAttributeName\": \"testAttribute\", \"brazeSubscribeEventNamePrefix\": \"testEventName\"}}"
    val cleanseList: CleanseList = CleanseList("TestNewsletter", List("a", "b", "c", "d", "e"), testBrazeData)
    decode[CleanseList](jsonString) should be(Right(cleanseList))
  }

  "The batch split logic" should "ignore an empty list" in {
    val cleanseList: CleanseList = CleanseList("TestNewsletter", Nil, testBrazeData)


    cleanseList.getCleanseListBatches(100) should be(Nil)
  }

  it should "return a List of cleanseLists with Lists of users of length `usersPerMessage`" in {
    val cleanseList: CleanseList = CleanseList("TestNewsletter", shortList, testBrazeData)

    cleanseList.getCleanseListBatches(2) should be(
      List(
        CleanseList("TestNewsletter", List("a", "b"), testBrazeData),
        CleanseList("TestNewsletter", List("c", "d"), testBrazeData),
        CleanseList("TestNewsletter", List("e"),testBrazeData)
      )
    )

  }
}

