package com.gu.newsletterlistcleanse.db

import com.simba.athena.jdbc.DataSource
import scalikejdbc.{ ConnectionPool, DataSourceConnectionPool }

case class DataLakeConfig(url: String, user: String, password: String)

object DataLake {
  def init(): Unit = {
    Class.forName("com.simba.athena.jdbc.Driver")
    val ds: DataSource = new DataSource()
    ds.setURL("jdbc:awsathena://AwsRegion=eu-west-1;")
    ds.setCustomProperty("S3OutputLocation", "s3://aws-athena-query-results-021353022223-eu-west-1/")
    ds.setCustomProperty("AwsCredentialsProviderClass", "com.gu.newsletterlistcleanse.NewsletterAWSCredentialProvider")
    ConnectionPool.singleton(new DataSourceConnectionPool(ds))
  }
}
