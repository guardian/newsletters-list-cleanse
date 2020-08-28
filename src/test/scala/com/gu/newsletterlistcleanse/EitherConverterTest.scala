package com.gu.newsletterlistcleanse

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import com.gu.newsletterlistcleanse.EitherConverter.EitherList


class EitherConverterTest extends AnyFlatSpec with Matchers {

  "The EitherList converter" should "return a Right with a list of successes if no errors occurred" in {

    val l: List[Either[Error, String]] = List(Right("a"), Right("b"), Right("c"))

    val result = l.toEitherList

    result should be(Right(List("a", "b", "c")))
  }

  it should "return a Left even if only one error occurs" in {
    val testErrorB: Error = new Error("b")
    val l: List[Either[Error, String]] = List(Right("a"), Left(testErrorB), Right("c"))

    l.toEitherList should be(Left(List(testErrorB)))
  }

  it should "return a Left with a list of all errors if more than one error occurred" in {
    val testErrorA: Error = new Error("a")
    val testErrorB: Error = new Error("b")
    val l: List[Either[Error, String]] = List(Left(testErrorA), Left(testErrorB), Right("c"))

    l.toEitherList should be(Left(List(testErrorA, testErrorB)))
  }
}
