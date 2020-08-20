package com.gu.newsletterlistcleanse.sqs

import com.amazonaws.regions.Regions
import com.amazonaws.services.sqs.model.SendMessageResult
import com.amazonaws.services.sqs.{AmazonSQS, AmazonSQSClientBuilder}
import com.gu.newsletterlistcleanse.NewsletterSQSAWSCredentialProvider
import org.slf4j.{Logger, LoggerFactory}

object AwsSQSSend {
  case class QueueName(value: String) extends AnyVal

  case class Payload(value: String) extends AnyVal

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

  def sendMessage(queueName: QueueName, payload: Payload): SendMessageResult = {
    val (sqsClient: AmazonSQS, queueUrl: String) = buildSqsClient(queueName)

    sqsClient.sendMessage(queueUrl, payload.value)
  }

}
