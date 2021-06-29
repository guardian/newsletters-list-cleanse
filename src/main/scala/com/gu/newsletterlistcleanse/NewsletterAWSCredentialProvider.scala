package com.gu.newsletterlistcleanse

import com.amazonaws.auth.{AWSCredentialsProviderChain, DefaultAWSCredentialsProviderChain}
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import software.amazon.awssdk.auth.credentials.{
  AwsCredentialsProvider => AwsCredentialsProviderV2,
  ProfileCredentialsProvider => ProfileCredentialsProviderV2
}

class NewsletterSQSAWSCredentialsProviderV1 extends AWSCredentialsProviderChain(
  new ProfileCredentialsProvider("identity"),
  DefaultAWSCredentialsProviderChain.getInstance())

object NewsletterSQSAwsCredentialsProviderV2 {
  val credentialsProvider: AwsCredentialsProviderV2 = ProfileCredentialsProviderV2.create("identity")
}