package com.gu.newsletterlistcleanse.db

case class ActiveListLength(newsletterName: String, listLength: Int)

object ActiveListLength {
  def getActiveListLength(listLengths: List[ActiveListLength], newsletterName: String): Int =
    listLengths.find(listLength => listLength.newsletterName == newsletterName).map(_.listLength).getOrElse(0)
}
