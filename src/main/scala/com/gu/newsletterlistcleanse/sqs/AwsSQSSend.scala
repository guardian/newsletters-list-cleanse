package com.gu.newsletterlistcleanse.sqs

import com.amazonaws.regions.Regions
import com.amazonaws.services.sqs.model.{SendMessageRequest, SendMessageResult}
import com.amazonaws.services.sqs.{AmazonSQSAsync, AmazonSQSAsyncClientBuilder}
import com.gu.newsletterlistcleanse.NewsletterSQSAWSCredentialProvider
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.Future

object AwsSQSSend {
  case class QueueName(value: String) extends AnyVal

  case class Payload(value: String) extends AnyVal

  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  private def buildSqsClient(queueName: QueueName): (AmazonSQSAsync, String) = {

    val sqsClient = AmazonSQSAsyncClientBuilder
      .standard()
      .withCredentials(new NewsletterSQSAWSCredentialProvider())
      .withRegion(Regions.EU_WEST_1)
      .build()

    val queueUrl = sqsClient.getQueueUrl(queueName.value).getQueueUrl
    (sqsClient, queueUrl)
  }

  def sendMessage(queueName: QueueName, payload: Payload): Future[SendMessageResult] = {
    val (sqsClient: AmazonSQSAsync, queueUrl: String) = buildSqsClient(queueName)

    val request: SendMessageRequest = new SendMessageRequest(queueUrl, payload.value)

    AwsAsync(sqsClient.sendMessageAsync, request)
  }
}
