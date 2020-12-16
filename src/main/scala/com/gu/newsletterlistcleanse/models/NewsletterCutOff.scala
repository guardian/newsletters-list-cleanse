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
  activeListLength: Int
)

object NewsletterCutOff {
  implicit val newsletterCutOffEncoder: Encoder[NewsletterCutOff] = deriveEncoder
  implicit val newsletterCutOffDecoder: Decoder[NewsletterCutOff] = deriveDecoder
}


case class NewsletterCutOffWithBraze(
  newsletterCutOff: NewsletterCutOff,
  brazeData: BrazeData
)
object NewsletterCutOffWithBraze {
  implicit val newsletterCutOffWithBrazeEncoder: Encoder[NewsletterCutOffWithBraze] = deriveEncoder
  implicit val newsletterCutOffWithBrazeDecoder: Decoder[NewsletterCutOffWithBraze] = deriveDecoder
}
