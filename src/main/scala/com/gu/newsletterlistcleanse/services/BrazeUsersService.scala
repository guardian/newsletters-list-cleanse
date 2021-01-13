package com.gu.newsletterlistcleanse.services

import java.time.Instant

import cats.data.EitherT
import cats.implicits._
import com.gu.newsletterlistcleanse.EitherConverter.EitherList
import com.gu.newsletterlistcleanse.NewsletterConfig
import com.gu.newsletterlistcleanse.models.{BrazeData, CleanseList}
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class BrazeUsersService(config: NewsletterConfig) {
  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  val brazeClient = new BrazeClient

  private val archiveFilterSet = config.archiveFilterSet

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

  private def updateUsers(userIds: List[String], brazeData: BrazeData): Future[Either[BrazeError, SimpleBrazeResponse]] = {
    val timestamp: Instant = Instant.now()
    val requests = for {
      userId <- userIds
    } yield BrazeNewsletterSubscriptionsUpdate(userId, Map((brazeData, config.subscribeUsers)))

    if (!config.dryRun) {
      brazeClient.updateUser(config.brazeApiToken, UserTrackRequest(requests, timestamp))
    } else {
      logger.info(s"Dry-run: Would have updated a batch of ${userIds.length} users")
      Future.successful(Right(SimpleBrazeResponse("success")))
    }
  }

  private def sendBrazeUpdates(cleanseLists: List[CleanseList], allInvalidUsers: Set[String]): Future[Either[List[BrazeError], List[SimpleBrazeResponse]]] = {
    // Braze caps us at 75 events and we have two events per user.
    val updateBatchSize = 37

    val brazeResponses: List[Future[Either[BrazeError, SimpleBrazeResponse]]] = for {
      cleanseList <- cleanseLists
      filteredUserIdList = cleanseList.userIdList.toSet -- (allInvalidUsers ++ archiveFilterSet)
      batchedUserIds = filteredUserIdList.grouped(updateBatchSize)
      batch <- batchedUserIds
      brazeData = cleanseList.brazeData
    } yield updateUsers(batch.toList, brazeData)

    Future.sequence(brazeResponses).map(brazeResponse => brazeResponse.toEitherList)
  }

  def getBrazeResults(cleanseLists: List[CleanseList], dryRun: Boolean): EitherT[Future, String, List[SimpleBrazeResponse]] = {
    val newsletterList = cleanseLists.map(_.newsletterName).mkString(", ")
    val totalUsers = cleanseLists.map(_.userIdList.length).sum
    logger.info(s"Processing $newsletterList, total of $totalUsers users to update")
    if (dryRun) logger.info("Running in dry-run mode, won't update Braze")
    val result = getAllInvalidUsers(cleanseLists)
      .flatMap {
        case Left(e) => Future.successful(Left(e))
        case Right(invalidUsers) =>
          sendBrazeUpdates(cleanseLists, invalidUsers.toSet)
      }
    EitherT(result)
      .leftMap { errors =>
        if (errors.length > 20) {
          logger.error("Encountered more than 20 errors, will only display the first 20")
        }
        errors.take(20).foreach(e => logger.error(s"HTTP ${e.code}, ${e.body}"))
        "Error(s) encountered while updating users in Braze"
      }
  }
}
