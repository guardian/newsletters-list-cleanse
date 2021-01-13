package com.gu.newsletterlistcleanse.models

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}


case class CleanseList(
  newsletterName: String,
  userIdList: List[String],
  activeListLength: Int,
  deletionCandidates: Int,
  brazeData: BrazeData
)

object CleanseList {
  implicit val cleanseListEncoder: Encoder[CleanseList] = deriveEncoder
  implicit val cleanseListDecoder: Decoder[CleanseList] = deriveDecoder
}
