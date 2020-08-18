package com.gu.newsletterlistcleanse.sqs

import com.amazonaws.regions.Regions
import com.amazonaws.services.sqs.model.SendMessageRequest
import com.amazonaws.services.sqs.{AmazonSQSAsync, AmazonSQSAsyncClientBuilder}
import com.gu.newsletterlistcleanse.NewsletterSQSAWSCredentialProvider
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}


object AwsSQSSend {
  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  def apply(queueName: QueueName)(payload: Payload):Future[Unit] = {
    val (sqsClient: AmazonSQSAsync, queueUrl: String) = buildSqsClient(queueName)
    logger.info(s"Sending message to SQS queue $queueUrl")
    val messageResult = AwsAsync(sqsClient.sendMessageAsync, new SendMessageRequest(queueUrl, payload.value))
    messageResult.transform {
      case Success(result) =>
        logger.info(s"Successfully sent message to $queueUrl: $result")
        Success(())
      case Failure(throwable) =>
        logger.error(s"Failed to send message to $queueUrl due to:", throwable)
        Failure(throwable)
    }
  }

  private def buildSqsClient(queueName: QueueName): (AmazonSQSAsync, String) = {

    val sqsClient = AmazonSQSAsyncClientBuilder
      .standard()
      .withCredentials(new NewsletterSQSAWSCredentialProvider())
      .withRegion(Regions.EU_WEST_1)
      .build()

    val queueUrl = sqsClient.getQueueUrl(queueName.value).getQueueUrl
    (sqsClient, queueUrl)
  }

    def sendSync(queueName: QueueName)(payload: Payload): Try[Unit] = {
      val (sqsClient: AmazonSQSAsync, queueUrl: String) = buildSqsClient(queueName)

      logger.info(s"Sending message to SQS queue $queueUrl")

      Try(sqsClient.sendMessage(new SendMessageRequest(queueUrl, payload.value))) match {
        case Success(result) =>
          logger.info(s"Successfully sent message to $queueUrl: $result")
          Success(())
        case Failure(throwable) =>
          logger.error(s"Failed to send message due to $queueUrl due to:", throwable)
          Failure(throwable)
      }

    }

  case class QueueName(value: String) extends AnyVal

  case class Payload(value: String) extends AnyVal
}
