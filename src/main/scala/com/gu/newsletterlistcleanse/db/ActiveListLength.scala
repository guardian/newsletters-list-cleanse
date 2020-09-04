package com.gu.newsletterlistcleanse.db

import scalikejdbc.WrappedResultSet

case class ActiveListLength(newsletterName: String, listLength: Int)

object ActiveListLength {
  def fromRow(rs: WrappedResultSet): ActiveListLength =
    ActiveListLength(
      newsletterName = rs.string("newsletter_name"),
      listLength = rs.int("list_length")
    )

  def getActiveListLength(listLengths: List[ActiveListLength], newsletterName: String): Int =
    listLengths.find(listLength => listLength.newsletterName == newsletterName).map(_.listLength).getOrElse(0)
}
