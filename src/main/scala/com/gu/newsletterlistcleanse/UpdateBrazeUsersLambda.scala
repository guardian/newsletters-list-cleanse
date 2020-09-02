package com.gu.newsletterlistcleanse

import java.time.Instant
import java.util.concurrent.TimeUnit

import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.gu.newsletterlistcleanse.EitherConverter.EitherList
import com.gu.newsletterlistcleanse.Newsletters.getIdentityNewsletterFromName
import com.gu.newsletterlistcleanse.braze.{BrazeClient, BrazeError, BrazeNewsletterSubscriptionsUpdate, BrazeResponse, UserTrackRequest}
import com.gu.newsletterlistcleanse.models.CleanseList
import io.circe
import io.circe.parser.decode
import org.slf4j.{Logger, LoggerFactory}


import scala.collection.JavaConverters._
import scala.concurrent.duration.Duration


class UpdateBrazeUsersLambda {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  val config: NewsletterConfig = NewsletterConfig.load()

  val timeout: Duration = Duration(15, TimeUnit.MINUTES)

  def handler(sqsEvent: SQSEvent): Unit = {
    val env = Env()
    logger.info(s"Starting $env")
    parseCleanseListSqsMessage(sqsEvent) match {
      case Right(cleanseLists) =>
        process(cleanseLists)
      case Left(parseErrors) =>
        parseErrors.foreach(e => logger.error(e.getMessage))
    }

  }

  def parseCleanseListSqsMessage(sqsEvent: SQSEvent): Either[List[circe.Error], List[CleanseList]] = {
    (for {
      message <- sqsEvent.getRecords.asScala.toList
    } yield {
      decode[CleanseList](message.getBody)
    }).toEitherList
  }

  def process(cleanseLists: List[CleanseList]): Either[List[BrazeError], List[BrazeResponse]] = {

    val brazeResponses = for {
      cleanseList <- cleanseLists
      newsletterName = cleanseList.newsletterName
      userIds = cleanseList.userIdList
      userId <- userIds
      identityNewsletter <- getIdentityNewsletterFromName(newsletterName)
    } yield {

      val apiKey: String = config.brazeApiToken
      val timestamp: Instant = Instant.now()
      val subscriptionsUpdate = BrazeNewsletterSubscriptionsUpdate(userId, Map((identityNewsletter, false)))

      val request: UserTrackRequest = UserTrackRequest.apply(subscriptionsUpdate, timestamp)

      BrazeClient.updateUser(apiKey, request)
    }

    brazeResponses.toEitherList

  }
}

object TestUpdateBrazeUsers {
  def main(args: Array[String]): Unit = {
    val cleanseLists = List(CleanseList("Editorial_AnimalsFarmed", List("user_1_jrb", "user_2_jrb")))
    val updateBrazeUsersLambda = new UpdateBrazeUsersLambda()
    println(updateBrazeUsersLambda.process(cleanseLists))
  }
}
