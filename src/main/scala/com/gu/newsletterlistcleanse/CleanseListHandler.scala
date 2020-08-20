package com.gu.newsletterlistcleanse

import com.gu.newsletterlistcleanse.models.CleanseList

case class CleanseListHandler(cleanseList: CleanseList){
  def getCleanseListBatches(usersPerMessage: Int): List[CleanseList] = {
    cleanseList.userIdList.grouped(usersPerMessage).toList
      .map(chunk => CleanseList(cleanseList.newsletterName, chunk ))
  }
}
