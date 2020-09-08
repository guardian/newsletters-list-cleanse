package com.gu.newsletterlistcleanse.sqs

import com.amazonaws.services.lambda.runtime.events.SQSEvent
import io.circe
import io.circe.Decoder
import io.circe.parser.decode
import scala.collection.JavaConverters._
import com.gu.newsletterlistcleanse.EitherConverter.EitherList



object ParseSqsMessage {

  def apply[T: Decoder](sqsEvent: SQSEvent): Either[List[circe.Error], List[T]] = {
    (for {
      message <- sqsEvent.getRecords.asScala.toList
    } yield {
      decode[T](message.getBody)
    }).toEitherList
  }
}
