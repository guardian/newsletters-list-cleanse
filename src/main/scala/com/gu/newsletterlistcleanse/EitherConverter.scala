package com.gu.newsletterlistcleanse

import scala.annotation.tailrec

object EitherConverter {
    implicit class EitherList[E, A](le: List[Either[E, A]]){
      def toEitherList: Either[E, List[A]] = {
        @tailrec
        def helper(list: List[Either[E, A]], acc: List[A]):
        Either[E, List[A]] = list match {
          case Nil => Right(acc)
          case x::xs => x match {
            case Left(e) => Left(e)
            case Right(v) => helper(xs, acc :+ v)
          }
        }
        helper(le, Nil)
      }
    }
}
