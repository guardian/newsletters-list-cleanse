package com.gu.newsletterlistcleanse.models

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}


case class CleanseList(newsletterName: String, userIdList: List[String], brazeData: BrazeData){
  def getCleanseListBatches(usersPerMessage: Int): List[CleanseList] = {
    this.userIdList.grouped(usersPerMessage).toList
      .map(chunk => CleanseList(this.newsletterName, chunk, this.brazeData))
  }
}

object CleanseList {
  implicit val cleanseListEncoder: Encoder[CleanseList] = deriveEncoder
  implicit val cleanseListDecoder: Decoder[CleanseList] = deriveDecoder
}
