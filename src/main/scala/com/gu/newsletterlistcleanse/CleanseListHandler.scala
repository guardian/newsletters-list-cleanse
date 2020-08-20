package com.gu.newsletterlistcleanse

import com.gu.newsletterlistcleanse.models.CleanseList

case class CleanseListHandler(cleanseList: CleanseList){
  def getCleanseListBatches(usersPerMessage: Int, messagesPerBatch: Int): List[Map[String, CleanseList]] = {
    val listOfCleanseLists = cleanseList.userIdList.grouped(usersPerMessage).toList
      .map(chunk => CleanseList(cleanseList.newsletterName, chunk ))

    listOfCleanseLists.zipWithIndex.map({case (element, index) => ((index+1).toString, element)})
      .grouped(messagesPerBatch).toList.map(_.toMap)
  }
}
