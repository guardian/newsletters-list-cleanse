package com.gu.newsletterlistcleanse

import com.amazonaws.auth.AWSCredentialsProvider
import com.gu.{AppIdentity, AwsIdentity}
import com.gu.conf.{ConfigurationLoader, SSMConfigurationLocation}

case class NewsletterConfig(
  serviceAccount: String,
  projectId: String,
  brazeApiToken: String,
  cutOffSqsUrl: String,
  cleanseListSqsUrl: String
)

object NewsletterConfig {
  def load(credentialProvider: AWSCredentialsProvider): NewsletterConfig = {
    val identity = AppIdentity.whoAmI(defaultAppName = "newsletter-list-cleanse", credentialProvider)
    val config = ConfigurationLoader.load(identity, credentialProvider) {
      case identity: AwsIdentity => SSMConfigurationLocation.default(identity)
    }
    NewsletterConfig(
      serviceAccount = config.getString("serviceAccount"),
      projectId = config.getString("projectId"),
      brazeApiToken = config.getString("brazeApiToken"),
      cutOffSqsUrl = config.getString("cutOffSqsUrl"),
      cleanseListSqsUrl = config.getString("cleanseListSqsUrl")
    )
  }
}
