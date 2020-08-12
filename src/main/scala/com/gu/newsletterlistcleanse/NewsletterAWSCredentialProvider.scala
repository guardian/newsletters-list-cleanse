package com.gu.newsletterlistcleanse

import com.simba.athena.amazonaws.auth.{ AWSCredentialsProviderChain, DefaultAWSCredentialsProviderChain }
import com.simba.athena.amazonaws.auth.profile.ProfileCredentialsProvider

class NewsletterAWSCredentialProvider extends AWSCredentialsProviderChain(
  new ProfileCredentialsProvider("ophan"),
  DefaultAWSCredentialsProviderChain.getInstance())
