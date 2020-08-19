package com.gu.newsletterlistcleanse.sqs

import com.amazonaws.regions.Regions
import com.amazonaws.services.sqs.model.{SendMessageBatchRequest, SendMessageBatchRequestEntry, SendMessageBatchResult, SendMessageRequest, SendMessageResult}
import com.amazonaws.services.sqs.{AmazonSQSAsync, AmazonSQSAsyncClientBuilder}
import com.gu.newsletterlistcleanse.NewsletterSQSAWSCredentialProvider
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import collection.JavaConverters._
import scala.util.{Failure, Success}



object AwsSQSSend {
  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  def apply[P <: Payload](queueName: QueueName)(payload : P):Future[Unit] = {
    val (sqsClient: AmazonSQSAsync, queueUrl: String) = buildSqsClient(queueName)

    val messageResult = payload match {
      case single: SinglePayload => sendAsync(sqsClient, queueUrl, single)
      case batch: BatchPayload => sendBatchAsync(sqsClient, queueUrl, batch)
    }

    logger.info(s"Sending message to SQS queue $queueUrl")
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

  def sendAsync(sqsClient:AmazonSQSAsync ,queueUrl: String, payload: SinglePayload): Future[SendMessageResult] = {
    AwsAsync(sqsClient.sendMessageAsync, new SendMessageRequest(queueUrl, payload.value))
  }

  def sendBatchAsync(sqsClient:AmazonSQSAsync ,queueUrl: String, batchPayload: BatchPayload): Future[SendMessageBatchResult] = {
    val batchRequest: java.util.List[SendMessageBatchRequestEntry] = batchPayload.value.map({case(id, payload) =>
      new SendMessageBatchRequestEntry(id, payload)}
    ).toList.asJava

    AwsAsync.apply[SendMessageBatchRequest, SendMessageBatchResult](
      sqsClient.sendMessageBatchAsync,
      new SendMessageBatchRequest(queueUrl, batchRequest)
    )
  }

  case class QueueName(value: String) extends AnyVal

  sealed trait Payload

  case class SinglePayload(value: String) extends Payload

  case class BatchPayload(value: Map[String,String]) extends Payload
}
