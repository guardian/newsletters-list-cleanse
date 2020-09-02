package com.gu.newsletterlistcleanse.braze

import java.time.Instant

import com.gu.identity.model.{EmailNewsletter, EmailNewsletters}
import scalaj.http.HttpResponse
import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.EncoderOps

case class BrazeResponse(message: String) {
  def isSuccessful: Boolean = message == "success" || message == "queued"
}
object BrazeResponse {
  implicit val brazeResponseEncoder: Encoder[BrazeResponse] = deriveEncoder
  implicit val brazeResponseDecoder: Decoder[BrazeResponse] = deriveDecoder
}

case class BrazeError(code: Int, body: String)

object BrazeError {
  def apply(response: HttpResponse[String]): BrazeError = BrazeError(response.code, response.body)
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

  def apply(externalId: String, sub: EmailNewsletter, timestamp: Instant, updateExistingOnlyField: Boolean = false): List[BrazeEvent] = {
    // Marketing need to be able to segment subscription events by campaign. To do this the campaign name must be in the name of the event,
    // (as braze can only segment by custom event name not property).
    val newsletterEventName = s"${sub.brazeSubscribeEventNamePrefix}_unsubscribe_email_date"

    val generalEventName = "EditorialUnsubscribe"
    List(
      BrazeEvent(externalId, generalEventName, timestamp.toString, BrazeEventProperties(sub.brazeSubscribeAttributeName), updateExistingOnlyField),
      BrazeEvent(externalId, newsletterEventName, timestamp.toString, BrazeEventProperties(sub.brazeSubscribeAttributeName), updateExistingOnlyField)
    )

  }
}

case class UserTrackRequest(attributes: Seq[BrazeNewsletterSubscriptionsUpdate], events: Seq[BrazeEvent])

object UserTrackRequest {
  def apply(userUpdate: BrazeNewsletterSubscriptionsUpdate, timestamp: Instant): UserTrackRequest = {
    val events = userUpdate.newsletterSubscriptions.flatMap { case (subscription, _) =>
      BrazeSubscribeEvent(userUpdate.externalId, subscription, timestamp)
    }
    UserTrackRequest(Seq(userUpdate), events.toSeq)
  }

  implicit val userTrackRequestEncoder: Encoder[UserTrackRequest] = new Encoder[UserTrackRequest] {
    override def apply(utr: UserTrackRequest): Json = Json.obj(
      ("attributes", utr.attributes.asJson),
      ("events", utr.events.asJson)
    )
  }

}
