package com.gu.newsletterlistcleanse.models

import java.time.ZonedDateTime

import io.circe.{Encoder, Decoder}
import io.circe.generic.semiauto.{deriveEncoder, deriveDecoder}

case class NewsletterCutOff(
  newsletterName: String,
  cutOffDate: ZonedDateTime,
  activeListLength: Int
)

object NewsletterCutOff {
  implicit val newsletterCutOffEncoder: Encoder[NewsletterCutOff] = deriveEncoder
  implicit val newsletterCutOffDecoder: Decoder[NewsletterCutOff] = deriveDecoder
}