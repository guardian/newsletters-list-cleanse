package com.gu.newsletterlistcleanse

import java.util.concurrent.TimeUnit

import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.gu.newsletterlistcleanse.EitherConverter.EitherList
import com.gu.newsletterlistcleanse.GetCleanseListLambda.{logger, parseCutoffsSqsMessage, process, timeout}
import com.gu.newsletterlistcleanse.braze.BrazeClient
import com.gu.newsletterlistcleanse.models.CleanseList
import io.circe
import io.circe.parser.decode
import org.slf4j.{Logger, LoggerFactory}
import scalaj.http.HttpResponse

import scala.collection.JavaConverters._
import scala.concurrent.Await
import scala.concurrent.duration.Duration


object UpdateBrazeUsersLambda {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  val timeout: Duration = Duration(15, TimeUnit.MINUTES)

  val brazeClient: BrazeClient = new BrazeClient()


  def handler(sqsEvent: SQSEvent): Unit = {
    val env = Env()
    logger.info(s"Starting $env")
    parseCleanseListSqsMessage(sqsEvent) match {
      case Right(cleanseLists) =>
        process(cleanseLists)
      case Left(parseError) =>
        logger.error(parseError.getMessage)
    }

  }

  def parseCleanseListSqsMessage(sqsEvent: SQSEvent): Either[circe.Error, List[CleanseList]] = {
    (for {
      message <- sqsEvent.getRecords.asScala.toList
    } yield {
      decode[CleanseList](message.getBody)
    }).toEitherList
  }

  def process(cleanseLists: List[CleanseList]): Either[Error, List[HttpResponse[String]]] = {

    for {
      cleanseList <- cleanseLists
      newsletterName = cleanseList.newsletterName
      userIds = cleanseList.userIdList
    } {
      ???
      // TODO: Create API request

      // TODO: Send API request
    }

  }
}

object TestUpdateBrazeUsers {
  def main(args: Array[String]): Unit = {
    println(UpdateBrazeUsersLambda.process(args.headOption.getOrElse("Alex"), Env()))
  }
}
