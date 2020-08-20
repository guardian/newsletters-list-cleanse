package com.gu.newsletterlistcleanse.sqs

import com.amazonaws.regions.Regions
import com.amazonaws.services.sqs.model.{SendMessageBatchRequestEntry, SendMessageBatchResult, SendMessageResult}
import com.amazonaws.services.sqs.{AmazonSQS, AmazonSQSClientBuilder}
import com.gu.newsletterlistcleanse.NewsletterSQSAWSCredentialProvider
import org.slf4j.{Logger, LoggerFactory}

import collection.JavaConverters._




object AwsSQSSend {
  case class QueueName(value: String) extends AnyVal

  sealed trait Payload

  case class SinglePayload(value: String) extends Payload

  case class BatchPayload(value: List[Map[String,String]]) extends Payload

  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  private def buildSqsClient(queueName: QueueName): (AmazonSQS, String) = {

    val sqsClient = AmazonSQSClientBuilder
      .standard()
      .withCredentials(new NewsletterSQSAWSCredentialProvider())
      .withRegion(Regions.EU_WEST_1)
      .build()

    val queueUrl = sqsClient.getQueueUrl(queueName.value).getQueueUrl
    (sqsClient, queueUrl)
  }

  def sendSingle(queueName: QueueName, payload: SinglePayload): SendMessageResult = {
    val (sqsClient: AmazonSQS, queueUrl: String) = buildSqsClient(queueName)

    sqsClient.sendMessage(queueUrl, payload.value)
  }

  def sendBatch(queueName: QueueName, batchPayload: BatchPayload): List[SendMessageBatchResult] = {
    val (sqsClient: AmazonSQS, queueUrl: String) = buildSqsClient(queueName)
    for (
      batch <- batchPayload.value
    ) yield {
      val batchRequest: java.util.List[SendMessageBatchRequestEntry] = batch.map({ case (id, payload) =>
        new SendMessageBatchRequestEntry(id, payload)
      }
      ).toList.asJava
      sqsClient.sendMessageBatch(queueUrl, batchRequest)
    }
  }

}
