package com.gu.newsletterlistcleanse.models

import io.circe.{Encoder, Decoder}
import io.circe.generic.semiauto.{deriveEncoder, deriveDecoder}

case class CleanseList(newsletterName: String, userIdList: List[String], dryRun: Boolean){
  def getCleanseListBatches(usersPerMessage: Int): List[CleanseList] = {
    this.userIdList.grouped(usersPerMessage).toList
      .map(chunk => CleanseList(this.newsletterName, chunk, this.dryRun))
  }
}

object CleanseList {
  implicit val cleanseListEncoder: Encoder[CleanseList] = deriveEncoder
  implicit val cleanseListDecoder: Decoder[CleanseList] = deriveDecoder
}
