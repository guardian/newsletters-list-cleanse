package com.gu.newsletterlistcleanse.models

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax._


case class CleanseList(newsletterName: String, userIdList: List[String], brazeData: BrazeData){
  def getCleanseListBatches(usersPerMessage: Int): List[CleanseList] = {
    this.userIdList.grouped(usersPerMessage).toList
      .map(chunk => CleanseList(this.newsletterName, chunk, this.brazeData))
  }
}

object CleanseList {
  implicit val cleanseListEncoder: Encoder[CleanseList] = new Encoder[CleanseList] {
    override def apply(cl: CleanseList): Json = Json.obj(
      ("newsletterName", cl.newsletterName.asJson),
      ("userIdList", cl.userIdList.asJson),
      ("brazeData", Json.obj(
        ("brazeSubscribeAttributeName", cl.brazeData.brazeSubscribeAttributeName.asJson),
        ("brazeSubscribeEventNamePrefix", cl.brazeData.brazeSubscribeEventNamePrefix.asJson)
      ))
    )
  }
  implicit val cleanseListDecoder: Decoder[CleanseList] = deriveDecoder
}
