package com.gu.newsletterlistcleanse

import java.time.Instant
import java.util.concurrent.TimeUnit

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.gu.newsletterlistcleanse.EitherConverter.EitherList
import com.gu.newsletterlistcleanse.Newsletters.getIdentityNewsletterFromName
import com.gu.newsletterlistcleanse.braze.{BrazeClient, BrazeError, BrazeNewsletterSubscriptionsUpdate, SimpleBrazeResponse, UserExportRequest, UserTrackRequest}
import com.gu.newsletterlistcleanse.models.CleanseList
import com.gu.identity.model.EmailNewsletter
import io.circe
import io.circe.parser.decode
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.JavaConverters._
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global


class UpdateBrazeUsersLambda {
  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  val credentialProvider: AWSCredentialsProvider = new NewsletterSQSAWSCredentialProvider()
  val apiKey: String = config.brazeApiToken
  val config: NewsletterConfig = NewsletterConfig.load(credentialProvider)

  val timeout: Duration = Duration(15, TimeUnit.MINUTES)

  def handler(sqsEvent: SQSEvent): Unit = {
    val env = Env()
    logger.info(s"Starting $env")
    parseCleanseListSqsMessage(sqsEvent) match {
      case Right(cleanseLists) =>
        val brazeResults = Await.result(process(cleanseLists), timeout)
        brazeResults match {
          case Left(brazeErrors)=>
            brazeErrors.foreach(e => logger.error(s"Error updating Braze with Code ${e.code}: ${e.body}"))
          case Right(_) => logger.info("Successfully updated Braze")
        }
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

  def getInvalidUsers(userIds: List[String]): Future[Either[BrazeError, List[String]]] = {
    val request: UserExportRequest = UserExportRequest(userIds)
    BrazeClient.getInvalidUsers(apiKey, request)
  }

  private def getAllInvalidUsers(cleanseLists: List[CleanseList]): Future[Either[List[BrazeError], List[String]]] = {
    val allInvalidUserTasks: List[Future[Either[BrazeError, List[String]]]] = for {
      cleanseList <- cleanseLists
      batchedUserIds = cleanseList.userIdList.grouped(50)
      batch <- batchedUserIds
    } yield getInvalidUsers(batch)

    Future.sequence(allInvalidUserTasks).map(invalidUsers => invalidUsers.toEitherList.map(_.flatten))
  }

  private def updateUsers(apiKey: String, userIds: List[String], identityNewsletter: EmailNewsletter) = {
    val timestamp: Instant = Instant.now()
    val requests = for {
      userId <- userIds
    } yield {
      val subscriptionsUpdate = BrazeNewsletterSubscriptionsUpdate(userId, Map((identityNewsletter, false)))

      UserTrackRequest(subscriptionsUpdate, timestamp)
    }
    BrazeClient.updateUser(apiKey, requests)
  }

  private def sendBrazeUpdates(cleanseLists: List[CleanseList], allInvalidUsers: Set[String]): Future[Either[List[BrazeError], List[SimpleBrazeResponse]]] = {
    // Braze caps us at 75 events and we have two events per user.
    val updateBatchSize = 37

    val brazeResponses: List[Future[Either[BrazeError, SimpleBrazeResponse]]] = for {
      cleanseList <- cleanseLists
      filteredUserIdList = cleanseList.userIdList.toSet -- allInvalidUsers
      batchedUserIds = filteredUserIdList.grouped(updateBatchSize)
      batch <- batchedUserIds
      newsletterName = cleanseList.newsletterName
      identityNewsletter <- getIdentityNewsletterFromName(newsletterName)
    } yield updateUsers(apiKey, batch.toList, identityNewsletter)

    Future.sequence(brazeResponses).map(brazeResponse => brazeResponse.toEitherList)
  }

  def process(cleanseLists: List[CleanseList]): Future[Either[List[BrazeError], List[SimpleBrazeResponse]]] = {
    logger.info(s"Processing ${cleanseLists.map(_.newsletterName).mkString(", ")}")
    getAllInvalidUsers(cleanseLists)
      .flatMap(allInvalidUsers => allInvalidUsers match {
        case Left(e) => Future.successful(Left(e))
        case Right(invalidUsers) =>
          sendBrazeUpdates(cleanseLists, invalidUsers.toSet)
      })
  }
}

object TestUpdateBrazeUsers {
  def main(args: Array[String]): Unit = {
    val cleanseLists = List(CleanseList("Editorial_AnimalsFarmed", List("user_1_jrb", "user_2_jrb")))
    val updateBrazeUsersLambda = new UpdateBrazeUsersLambda()
    println(updateBrazeUsersLambda.process(cleanseLists))
  }
}
