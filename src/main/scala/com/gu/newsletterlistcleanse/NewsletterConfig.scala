package com.gu.newsletterlistcleanse

import com.gu.{AppIdentity, AwsIdentity}
import com.gu.conf.{ConfigurationLoader, SSMConfigurationLocation}

case class NewsletterConfig(
  serviceAccount: String,
  brazeApiToken: String
)

object NewsletterConfig {
  def load(): NewsletterConfig = {
    val identity = AppIdentity.whoAmI(defaultAppName = "newsletter-list-cleanse")
    val config = ConfigurationLoader.load(identity) {
      case identity: AwsIdentity => SSMConfigurationLocation.default(identity)
    }
    NewsletterConfig(
      serviceAccount = config.getString("serviceAccount"),
      brazeApiToken = config.getString("brazeApiToken")
    )
  }
}
