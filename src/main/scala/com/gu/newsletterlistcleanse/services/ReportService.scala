package com.gu.newsletterlistcleanse.services

import java.text.DecimalFormat

import com.amazonaws.services.sns.AmazonSNS
import com.gu.newsletterlistcleanse.{Env, NewsletterConfig}
import com.gu.newsletterlistcleanse.models.CleanseList
import org.slf4j.{Logger, LoggerFactory}

class ReportService(amazonSns: AmazonSNS, config: NewsletterConfig) {
  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  private val percentageFormat = new DecimalFormat("#.##%")

  def sendReport(cleanseLists: List[CleanseList], env: Env, dryRun: Boolean): Unit = {
    val lines = cleanseLists
      .sortBy(_.newsletterName)
      .map { cl =>
        val newCount = cl.activeListLength - cl.deletionCandidates
        val percentage: Double = if (cl.activeListLength != 0) {
          (cl.deletionCandidates.toDouble / cl.activeListLength.toDouble)
        } else {
          0
        }
        val formattedPercentage = percentageFormat.format(percentage)
        s"${cl.newsletterName}\t${cl.deletionCandidates}\t${cl.activeListLength}\t$newCount\t$formattedPercentage"
      }
      .mkString("\n")

    val prefix = if (dryRun) "[DryRun]" else "[Live]"
    val subject = s"$prefix Newsletter cleanse report for ${env.stage}"
    val dryRunWarning = if (dryRun) {
      "This is a dry run, the live run will happen next Tuesday. Contact the newsletter team if anything looks off."
    } else ""

    val header = s"Newsletter\tDeleted\tList Size\tList Size after\tCleanse Percentage"

    val report =
      s"""$dryRunWarning
         |$header
         |$lines
         |""".stripMargin

    logger.info("Sending cleanse report")
    amazonSns.publish(config.snsTopicArn, report, subject)
  }
}
