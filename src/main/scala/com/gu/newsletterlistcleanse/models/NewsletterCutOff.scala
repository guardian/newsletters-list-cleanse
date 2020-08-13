package com.gu.newsletterlistcleanse.models

import java.time.ZonedDateTime

case class NewsletterCutOff(
  newsletterName: String,
  cutOffDate: ZonedDateTime
)
