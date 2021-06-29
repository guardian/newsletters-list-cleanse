package com.gu.newsletterlistcleanse

import com.amazonaws.auth.{AWSCredentialsProviderChain, DefaultAWSCredentialsProviderChain}
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import software.amazon.awssdk.auth.credentials.{
  AwsCredentialsProviderChain => AwsCredentialsProviderChainV2,
  AwsCredentialsProvider => AwsCredentialsProviderV2,
  ProfileCredentialsProvider => ProfileCredentialsProviderV2,
  DefaultCredentialsProvider => DefaultCredentialsProviderV2
}

class NewsletterSQSAWSCredentialsProviderV1 extends AWSCredentialsProviderChain(
  new ProfileCredentialsProvider("identity"),
  DefaultAWSCredentialsProviderChain.getInstance())

object NewsletterSQSAwsCredentialsProviderV2 {
  val credentialsProvider: AwsCredentialsProviderV2 = AwsCredentialsProviderChainV2
    .builder()
    .addCredentialsProvider(ProfileCredentialsProviderV2.create("identity"))
    .addCredentialsProvider(DefaultCredentialsProviderV2.create())
    .build()
}