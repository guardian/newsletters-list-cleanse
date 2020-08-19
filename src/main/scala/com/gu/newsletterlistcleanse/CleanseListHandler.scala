package com.gu.newsletterlistcleanse

import com.gu.newsletterlistcleanse.models.CleanseList

case class CleanseListHandler(cleanseList: CleanseList){
  def getCleanseListBatches(maxBatchSize: Int): Map[String, CleanseList] = {
    cleanseList.userIdList.grouped(maxBatchSize).toList
      .map(chunk => CleanseList(cleanseList.newsletterName, chunk ))
      .zipWithIndex
      .map({case (element, index) => ((index+1).toString, element)}).toMap
  }
}
