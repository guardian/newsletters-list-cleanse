package com.gu.newsletterlistcleanse.models

import io.circe.{Encoder, Decoder}
import io.circe.generic.semiauto.{deriveEncoder, deriveDecoder}

case class CleanseList(newsletterName: String, userIdList: List[String])

object CleanseList {
  implicit val cleanseListEncoder: Encoder[CleanseList] = deriveEncoder
  implicit val cleanseListDecoder: Decoder[CleanseList] = deriveDecoder
}