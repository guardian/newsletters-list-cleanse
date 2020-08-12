import sbtassembly.AssemblyPlugin.autoImport.{assemblyJarName, assemblyMergeStrategy}
import sbtassembly.MergeStrategy

name := "newsletter-list-cleanse"

organization := "com.gu"

description:= "A monthly job to clean newsletter mailing lists of lapsed subscribers."

version := "1.0"

scalaVersion := "2.13.3"

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding", "UTF-8",
  "-target:jvm-1.8",
  "-Ywarn-dead-code"
)

enablePlugins(RiffRaffArtifact)

libraryDependencies ++= Seq(
  "com.amazonaws" % "aws-lambda-java-core" % "1.2.1",
  "com.amazonaws" % "aws-lambda-java-log4j2" % "1.2.0",
  "org.apache.logging.log4j" % "log4j-slf4j-impl" % "2.13.3",
  "org.slf4j" % "slf4j-api" % "1.7.30",
  "org.scalikejdbc" %% "scalikejdbc" % "3.5.0",
  "com.syncron.amazonaws" % "simba-athena-jdbc-driver" % "2.0.2"
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
