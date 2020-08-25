package com.gu.newsletterlistcleanse

import com.amazonaws.auth.{AWSCredentialsProviderChain, DefaultAWSCredentialsProviderChain}
import com.amazonaws.auth.profile.ProfileCredentialsProvider

class NewsletterAWSCredentialProvider extends AWSCredentialsProviderChain(
  new ProfileCredentialsProvider("ophan"),
  DefaultAWSCredentialsProviderChain.getInstance())

class NewsletterSQSAWSCredentialProvider extends AWSCredentialsProviderChain(
  new ProfileCredentialsProvider("identity"),
  DefaultAWSCredentialsProviderChain.getInstance())
