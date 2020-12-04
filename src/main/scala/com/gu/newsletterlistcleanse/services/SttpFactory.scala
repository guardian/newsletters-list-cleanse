package com.gu.newsletterlistcleanse.services

import org.asynchttpclient.DefaultAsyncHttpClientConfig
import org.asynchttpclient.filter.ThrottleRequestFilter
import sttp.client.asynchttpclient.WebSocketHandler
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend
import sttp.client.{SttpBackend, SttpBackendOptions}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.concurrent.duration.FiniteDuration

object SttpFactory {

  def createSttpBackend(timeout: FiniteDuration = 5.seconds, concurrencyLimit: Int = 3): SttpBackend[Future, Nothing, WebSocketHandler] = {

    val sttpOptions = SttpBackendOptions.connectionTimeout(timeout)
    val adjustFunction: DefaultAsyncHttpClientConfig.Builder => DefaultAsyncHttpClientConfig.Builder =
      _.addRequestFilter(new ThrottleRequestFilter(concurrencyLimit)).setMaxConnections(concurrencyLimit)

    AsyncHttpClientFutureBackend.usingConfigBuilder(adjustFunction, sttpOptions)
  }

}
