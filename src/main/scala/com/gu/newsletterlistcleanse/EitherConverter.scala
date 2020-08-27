package com.gu.newsletterlistcleanse

import scala.annotation.tailrec

object EitherConverter {
  implicit class EitherList[E, A](le: List[Either[E, A]]){
    def toEitherList: Either[List[E], List[A]] = {
      @tailrec
      def helper(list: List[Either[E, A]], acc: Either[List[E], List[A]]): Either[List[E], List[A]] =
        list match {
          case Nil => acc
          case x :: xs => x match {
            case Left(e) => {
              acc match {
                case Left(es) => helper(xs, Left(e :: es))
                case Right(_) => helper(xs, Left(List(e)) )
              }
            }
            case Right(a) => {
              acc match {
                case Left(es) => helper(xs, Left(es))
                case Right(as) => helper(xs, Right(a :: as))
              }
            }
          }
        }
      helper(le.reverse, Right(Nil))
    }

    def toEitherListFold: Either[List[E], List[A]] = {
      le.foldRight[Either[List[E], List[A]]](Right(Nil)) { (ea, acc) =>
        ea match {
          case Left(e) => {
            acc match {
              case Left(es) => Left(e :: es)
              case Right(_) => Left(List(e))
            }
          }
          case Right(a) => {
            acc match {
              case Left(es) => Left(es)
              case Right(as) => Right(a :: as)
            }
          }
        }
      }
    }

    def toEitherListFold2: Either[List[E], List[A]] = {
      le.foldRight[Either[List[E], List[A]]](Right(Nil)) {
        case (Left(e), Left(es)) =>
          Left(e :: es)
        case (Left(e), Right(_)) =>
          Left(List(e)) // Throw away accumulated successes and only track errors
        case (Right(_), Left(es)) =>
          Left(es)
        case (Right(a), Right(as)) =>
          Right(a :: as) // Keep accumulating successes
      }
    }

  }
}
