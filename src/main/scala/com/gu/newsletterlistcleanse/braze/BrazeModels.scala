package com.gu.newsletterlistcleanse.braze

import java.time.Instant

import com.gu.identity.model.IdentityNewsletter
import scalaj.http.HttpResponse
import io.circe.{Encoder, Decoder}
import io.circe.generic.semiauto.{deriveEncoder, deriveDecoder}

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
                                              newsletterSubscriptions: Map[IdentityNewsletter, Boolean])

case class BrazeEventProperties(campaign_name: String)

case class BrazeEvent(external_id: String,
                      name: String,
                      time: String,
                      properties: BrazeEventProperties,
                      updateExistingOnlyField: Boolean = false)

object BrazeSubscribeEvent {

  def apply(externalId: String, sub: IdentityNewsletter, isSubscribed: Boolean, timestamp: Instant, updateExistingOnlyField: Boolean = false): List[BrazeEvent] = {
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

case class UserTrackRequest(api_key: String, attributes: Seq[BrazeNewsletterSubscriptionsUpdate], events: Seq[BrazeEvent])

object UserTrackRequest {
  def apply(api_key: String, userUpdate: BrazeNewsletterSubscriptionsUpdate, timestamp: Instant): UserTrackRequest = {
    val events = userUpdate.newsletterSubscriptions.flatMap { case (subscription, isSubscribed) =>
      BrazeSubscribeEvent(userUpdate.externalId, subscription, isSubscribed, timestamp)
    }
    UserTrackRequest(api_key, Seq(userUpdate), events.toSeq)
  }

  implicit val userTrackRequestEncoder: Encoder[UserTrackRequest] = deriveEncoder
  implicit val userTrackRequestDecoder: Decoder[UserTrackRequest] = deriveDecoder

}
