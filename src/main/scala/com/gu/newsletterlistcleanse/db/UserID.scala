package com.gu.newsletterlistcleanse.db

import scalikejdbc.WrappedResultSet

case class UserID(
  userId: String
)

object UserID {

  def fromRow(rs: WrappedResultSet): UserID =
    UserID(userId = rs.string("user_id"))
}
