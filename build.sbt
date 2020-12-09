import sbtassembly.AssemblyPlugin.autoImport.{assemblyJarName, assemblyMergeStrategy}
import sbtassembly.MergeStrategy

name := "newsletter-list-cleanse"

organization := "com.gu"

description:= "A monthly job to clean newsletter mailing lists of lapsed subscribers."

version := "1.0"

scalaVersion := "2.12.12"

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding", "UTF-8",
  "-target:jvm-1.8",
  "-Ywarn-dead-code",
  "-Ypartial-unification",
)

enablePlugins(RiffRaffArtifact)

resolvers += "Guardian Platform Bintray" at "https://dl.bintray.com/guardian/platforms"

libraryDependencies ++= Seq(
  "com.gu" %% "simple-configuration-ssm" % "1.5.2",
  "com.amazonaws" % "aws-lambda-java-core" % "1.2.1",
  "com.amazonaws" % "aws-lambda-java-events" % "2.2.9",
  "com.amazonaws" % "aws-lambda-java-log4j2" % "1.2.0",
  "com.amazonaws" % "aws-java-sdk-sqs" % "1.11.842",
  "com.amazonaws" % "aws-java-sdk-s3" % "1.11.842",
  "org.apache.logging.log4j" % "log4j-slf4j-impl" % "2.13.3",
  "org.slf4j" % "slf4j-api" % "1.7.30",
  "com.google.cloud" % "google-cloud-bigquery" % "1.116.10",
  "org.scalatest" %% "scalatest" % "3.2.0" % "test",
  "io.circe" %% "circe-core" % "0.12.3",
  "io.circe" %% "circe-generic" % "0.12.3",
  "io.circe" %% "circe-parser"% "0.12.3",
  "com.softwaremill.sttp.client" %% "core" % "2.2.7",
  "com.softwaremill.sttp.client" %% "async-http-client-backend-future" % "2.2.7",
  "org.typelevel" %% "cats-core" % "2.1.1",
)
assemblyJarName := s"${name.value}.jar"
assemblyMergeStrategy in assembly := {
  case "META-INF/MANIFEST.MF" => MergeStrategy.discard
  case "META-INF/org/apache/logging/log4j/core/config/plugins/Log4j2Plugins.dat" => new MergeLog4j2PluginCachesStrategy
  case _ => MergeStrategy.first
}
riffRaffPackageType := assembly.value
riffRaffUploadArtifactBucket := Option("riffraff-artifact")
riffRaffUploadManifestBucket := Option("riffraff-builds")
riffRaffArtifactResources += (file("cfn.yaml"), s"${name.value}-cfn/cfn.yaml")
