package com.gu.newsletterlistcleanse.sqs

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.regions.Regions
import com.amazonaws.services.sqs.model.{SendMessageRequest, SendMessageResult}
import com.amazonaws.services.sqs.{AmazonSQSAsync, AmazonSQSAsyncClientBuilder}
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.Future

object AwsSQSSend {

  case class Payload(value: String) extends AnyVal

  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  def buildSqsClient(credentialProvider: AWSCredentialsProvider): AmazonSQSAsync = {
    AmazonSQSAsyncClientBuilder
      .standard()
      .withCredentials(credentialProvider)
      .withRegion(Regions.EU_WEST_1)
      .build()
  }

  def sendMessage(sqsClient: AmazonSQSAsync, queueUrl: String, payload: Payload): Future[SendMessageResult] = {
    val request: SendMessageRequest = new SendMessageRequest(queueUrl, payload.value)

    AwsAsync(sqsClient.sendMessageAsync, request)
  }
}
