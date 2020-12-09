package com.gu.newsletterlistcleanse.models

import java.time.ZonedDateTime

import io.circe.{Encoder, Decoder}
import io.circe.generic.semiauto.{deriveEncoder, deriveDecoder}

case class BrazeData (
  brazeSubscribeAttributeName: String,
  brazeSubscribeEventNamePrefix: String
)

object BrazeData {
  implicit val newsletterCutOffEncoder: Encoder[BrazeData] = deriveEncoder
  implicit val newsletterCutOffDecoder: Decoder[BrazeData] = deriveDecoder
}
case class NewsletterCutOff(
  newsletterName: String,
  cutOffDate: ZonedDateTime,
  activeListLength: Int,
  brazeData: BrazeData
)

object NewsletterCutOff {
  def apply(newsletterName: String, cutOffDate: ZonedDateTime, activeListLength: Int) : (BrazeData => NewsletterCutOff)
  = NewsletterCutOff(newsletterName, cutOffDate, activeListLength, _: BrazeData)
  implicit val newsletterCutOffEncoder: Encoder[NewsletterCutOff] = deriveEncoder
  implicit val newsletterCutOffDecoder: Decoder[NewsletterCutOff] = deriveDecoder
}
