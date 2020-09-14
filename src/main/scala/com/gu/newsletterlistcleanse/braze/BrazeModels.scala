package com.gu.newsletterlistcleanse.braze

import java.time.Instant

import com.gu.identity.model.{EmailNewsletter, EmailNewsletters}
import sttp.client._
import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.EncoderOps

trait BrazeResponse {
  val message: String
  def isSuccessful: Boolean
}
case class SimpleBrazeResponse(message: String) extends BrazeResponse {
  override def isSuccessful: Boolean = message == "success" || message == "queued"
}

object SimpleBrazeResponse {
  implicit val simpleBrazeResponseEncoder: Encoder[SimpleBrazeResponse] = deriveEncoder
  implicit val simpleBrazeResponseDecoder: Decoder[SimpleBrazeResponse] = deriveDecoder
}

case class ExportIdBrazeResponse(message: String, invalidUserIds: List[String],
                                 users: List[Map[String,String]]) extends BrazeResponse {
  override def isSuccessful: Boolean = message == "success" || message == "queued"
}
object ExportIdBrazeResponse {
  implicit val exportIdBrazeResponseEncoder: Encoder[ExportIdBrazeResponse] = deriveEncoder
  implicit val exportIdBrazeResponseDecoder: Decoder[ExportIdBrazeResponse] =
    Decoder.forProduct3("message", "invalid_user_ids", "users")(ExportIdBrazeResponse.apply)
}

case class BrazeError(code: Int, body: String)

object BrazeError {
  def apply(response: Response[String]): BrazeError = BrazeError(response.code.code, response.body)
}

case class BrazeNewsletterSubscriptionsUpdate(externalId: String,
                                              newsletterSubscriptions: Map[EmailNewsletter, Boolean])

object BrazeNewsletterSubscriptionsUpdate {
  implicit val subscriptionUpdateEncoder: Encoder[BrazeNewsletterSubscriptionsUpdate] =
    new Encoder[BrazeNewsletterSubscriptionsUpdate] {
      override def apply(update: BrazeNewsletterSubscriptionsUpdate): Json = {
        val jsonSubs = update.newsletterSubscriptions.map {
          // This case only relevant whilst migrating email_subscribe_today_uk custom attribute
          case (newsLetter: EmailNewsletter, isSubscribed) if newsLetter == EmailNewsletters.guardianTodayUk =>
            Map(
              newsLetter.brazeSubscribeAttributeName -> Json.fromBoolean(isSubscribed),
              "email_subscribe_today_uk" -> Json.fromBoolean(isSubscribed)
            )
          case (newsLetter, isSubscribed) =>
            Map(newsLetter.brazeSubscribeAttributeName -> Json.fromBoolean(isSubscribed))
        }.fold(Map.empty)(_ ++ _)
        (jsonSubs ++ Map("external_id" -> Json.fromString(update.externalId))).asJson
      }
  }
}

case class BrazeEventProperties(campaign_name: String)

case class BrazeEvent(external_id: String,
                      name: String,
                      time: String,
                      properties: BrazeEventProperties,
                      updateExistingOnlyField: Boolean = false)

object BrazeEvent {
  implicit val brazeEventEncoder: Encoder[BrazeEvent] = new Encoder[BrazeEvent] {
    override def apply(event: BrazeEvent): Json = Json.obj(
        "external_id" -> Json.fromString(event.external_id),
        "name" -> Json.fromString(event.name),
        "properties" -> Map("campaign_name" -> event.properties.campaign_name).asJson,
        "time" -> Json.fromString(event.time),
    )
  }
}

object BrazeSubscribeEvent {

  def apply(externalId: String, sub: EmailNewsletter, isSubscribed: Boolean, timestamp: Instant, updateExistingOnlyField: Boolean = false): List[BrazeEvent] = {
    // Marketing need to be able to segment subscription events by campaign. To do this the campaign name must be in the name of the event,
    // (as braze can only segment by custom event name not property).
    val newsletterEventName = if (isSubscribed) s"${sub.brazeSubscribeEventNamePrefix}_subscribe_email_date" else s"${sub.brazeSubscribeEventNamePrefix}_unsubscribe_email_date"
    val generalEventName = if (isSubscribed) "EditorialSubscribe" else "EditorialUnsubscribe"
    List(
      BrazeEvent(externalId, generalEventName, timestamp.toString, BrazeEventProperties(sub.brazeSubscribeAttributeName), updateExistingOnlyField),
      BrazeEvent(externalId, newsletterEventName, timestamp.toString, BrazeEventProperties(sub.brazeSubscribeAttributeName), updateExistingOnlyField)
    )
  }
}

case class UserTrackRequest(attributes: Seq[BrazeNewsletterSubscriptionsUpdate], events: Seq[BrazeEvent])

object UserTrackRequest {
  def apply(userUpdates: List[BrazeNewsletterSubscriptionsUpdate], timestamp: Instant): UserTrackRequest = {
    val events = for {
      userUpdate <- userUpdates
      (subscription, isSubscribed) <- userUpdate.newsletterSubscriptions
      event <- BrazeSubscribeEvent(userUpdate.externalId, subscription, isSubscribed, timestamp)
    } yield event

    UserTrackRequest(userUpdates, events)
  }

  implicit val userTrackRequestEncoder: Encoder[UserTrackRequest] = new Encoder[UserTrackRequest] {
    override def apply(utr: UserTrackRequest): Json = Json.obj(
      ("attributes", utr.attributes.asJson),
      ("events", utr.events.asJson)
    )
  }

}

case class UserExportRequest(userIds: List[String])

object UserExportRequest {
  implicit val userExportRequestEncoder: Encoder[UserExportRequest] = new Encoder[UserExportRequest] {
    override def apply(uer: UserExportRequest): Json = Json.obj(
      ("external_ids", uer.userIds.asJson),
      ("fields_to_export", List("external_id").asJson)
    )
  }
}
