package com.gu.newsletterlistcleanse.services

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class NewslettersSpec extends AnyFlatSpec with Matchers {

  val newsletters = new Newsletters()

  val testAllNewsletters = List(Newsletter("test1", "testAttribute", "testEventName"),
    Newsletter("test2", "testAttribute", "testEventName"),
    Newsletter("test3", "testAttribute", "testEventName")
  )

  "The fetchNewsletters logic" should "return all Newsletters if no selection is passed" in {
    newsletters.filterNewsletters(testAllNewsletters).length should be(3)
  }

  it should "return a filtered set of newsletters if a filterSelection is passed" in {
    newsletters.filterNewsletters(testAllNewsletters, List("test1")).length should be(1)
  }
}
