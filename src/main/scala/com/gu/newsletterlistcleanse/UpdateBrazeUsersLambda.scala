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
import com.gu.newsletterlistcleanse.sqs.SqsMessageParser
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Try


class UpdateBrazeUsersLambda {
  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  val credentialProvider: AWSCredentialsProvider = new NewsletterSQSAWSCredentialProvider()
  val config: NewsletterConfig = NewsletterConfig.load(credentialProvider)
  val timeout: Duration = Duration(15, TimeUnit.MINUTES)

  val brazeClient = new BrazeClient

  private val archiveFilterSet = config.archiveFilterSet

  def handler(sqsEvent: SQSEvent): Unit = {
    val env = Env()
    logger.info(s"Starting $env")
    SqsMessageParser.parse[CleanseList](sqsEvent) match {
      case Right(cleanseLists) =>
        resolveBrazeUpdates(cleanseLists)
      case Left(parseErrors) =>
        parseErrors.foreach(e => logger.error(e.getMessage))
    }
  }

  private def resolveBrazeUpdates(cleanseLists: List[CleanseList]) = {
    val brazeResults = Await.result(getBrazeResults(cleanseLists), timeout)
    brazeResults match {
      case Left(brazeErrors) =>
        brazeErrors.foreach(e => logger.error(s"Error updating Braze with Code ${e.code}: ${e.body}"))
      case Right(_) => logger.info("Successfully updated Braze")
    }
  }

  private def getInvalidUsers(userIds: List[String]): Future[Either[BrazeError, List[String]]] =
    brazeClient.getInvalidUsers(config.brazeApiToken, UserExportRequest(userIds))

  private def getAllInvalidUsers(cleanseLists: List[CleanseList]): Future[Either[List[BrazeError], List[String]]] = {
    val allInvalidUserTasks: List[Future[Either[BrazeError, List[String]]]] = for {
      cleanseList <- cleanseLists
      batchedUserIds = cleanseList.userIdList.grouped(50)
      batch <- batchedUserIds
    } yield getInvalidUsers(batch)

    Future.sequence(allInvalidUserTasks).map(invalidUsers => invalidUsers.toEitherList.map(_.flatten))
  }

  private def updateUsers(userIds: List[String], identityNewsletter: EmailNewsletter) = {
    val timestamp: Instant = Instant.now()
    val requests = for {
      userId <- userIds
    } yield BrazeNewsletterSubscriptionsUpdate(userId, Map((identityNewsletter, false)))

    brazeClient.updateUser(config.brazeApiToken, UserTrackRequest(requests, timestamp))
  }

  private def sendBrazeUpdates(cleanseLists: List[CleanseList], allInvalidUsers: Set[String]): Future[Either[List[BrazeError], List[SimpleBrazeResponse]]] = {
    // Braze caps us at 75 events and we have two events per user.
    val updateBatchSize = 37

    val brazeResponses: List[Future[Either[BrazeError, SimpleBrazeResponse]]] = for {
      cleanseList <- cleanseLists
      filteredUserIdList = cleanseList.userIdList.toSet -- (allInvalidUsers ++ archiveFilterSet)
      batchedUserIds = filteredUserIdList.grouped(updateBatchSize)
      batch <- batchedUserIds
      newsletterName = cleanseList.newsletterName
      identityNewsletter <- getIdentityNewsletterFromName(newsletterName)
    } yield updateUsers(batch.toList, identityNewsletter)

    Future.sequence(brazeResponses).map(brazeResponse => brazeResponse.toEitherList)
  }

  def getBrazeResults(cleanseLists: List[CleanseList]): Future[Either[List[BrazeError], List[SimpleBrazeResponse]]] = {
    logger.info(s"Processing ${cleanseLists.map(_.newsletterName).mkString(", ")}")
    getAllInvalidUsers(cleanseLists)
      .flatMap {
        case Left(e) => Future.successful(Left(e))
        case Right(invalidUsers) =>
          sendBrazeUpdates(cleanseLists, invalidUsers.toSet)
      }
  }
}

object TestUpdateBrazeUsers {
  def main(args: Array[String]): Unit = {
    val cleanseLists = List(CleanseList("Editorial_AnimalsFarmed", List("user_1_jrb", "user_2_jrb", "mystery_user 1")))
    val updateBrazeUsersLambda = new UpdateBrazeUsersLambda()
    val result = Try(Await.result(updateBrazeUsersLambda.getBrazeResults(cleanseLists), updateBrazeUsersLambda.timeout))
    Await.result(updateBrazeUsersLambda.brazeClient.sttpBackend.close(), Duration(15, TimeUnit.SECONDS))
    println(result)
  }
}
