package com.gu.newsletterlistcleanse

import com.gu.{AppIdentity, AwsIdentity}
import com.gu.conf.{ConfigurationLoader, SSMConfigurationLocation}
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider

import scala.collection.JavaConverters._

case class NewsletterConfig(
  serviceAccount: String,
  projectId: String,
  brazeApiToken: String,
  archiveFilterSet: Set[String],
  dryRun: Boolean,
  backupBucketName: String,
  subscribeUsers: Boolean,
  snsTopicArn: String,
)

object NewsletterConfig {
  def load(credentialProvider: AwsCredentialsProvider): NewsletterConfig = {
    val identity = AppIdentity.whoAmI(defaultAppName = "newsletter-list-cleanse", credentialProvider)
    val config = ConfigurationLoader.load(identity, credentialProvider) {
      case identity: AwsIdentity => SSMConfigurationLocation(s"/${identity.stack}/${identity.app}/${identity.stage}")
    }
    NewsletterConfig(
      serviceAccount = config.getString("serviceAccount"),
      projectId = config.getString("projectId"),
      brazeApiToken = config.getString("brazeApiToken"),
      archiveFilterSet = config.getStringList("archiveFilterList").asScala.toSet,
      dryRun = if (config.hasPathOrNull("dryRun")) config.getBoolean("dryRun") else true,
      backupBucketName = config.getString("backupBucketName"),
      subscribeUsers = if (config.hasPathOrNull("subscribeUsers")) config.getBoolean("subscribeUsers") else false,
      snsTopicArn = config.getString("snsTopicArn"),
    )
  }
}
