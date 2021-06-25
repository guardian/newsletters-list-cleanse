package com.gu.newsletterlistcleanse

import com.amazonaws.auth.{AWSCredentialsProviderChain, DefaultAWSCredentialsProviderChain}
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import software.amazon.awssdk.auth.credentials.{
  AwsCredentialsProvider => AwsCredentialsProviderV2,
  ProfileCredentialsProvider => ProfileCredentialsProviderV2,
}

object NewsletterSQSAWSCredentialProvider {

  val credentialsProviderV2: AwsCredentialsProviderV2 = ProfileCredentialsProviderV2.create("identity")
  val credentialsProviderV1: NewsletterSQSAWSCredentialProvider = new NewsletterSQSAWSCredentialProvider()

  class NewsletterSQSAWSCredentialProvider extends AWSCredentialsProviderChain(
    new ProfileCredentialsProvider("identity"),
    DefaultAWSCredentialsProviderChain.getInstance())

}
