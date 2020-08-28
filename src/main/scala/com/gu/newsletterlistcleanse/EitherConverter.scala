package com.gu.newsletterlistcleanse

object EitherConverter {
  implicit class EitherList[E, A](le: List[Either[E, A]]){
    def toEitherList: Either[List[E], List[A]] = {
      le.foldRight[Either[List[E], List[A]]](Right(Nil)) {
        case (Left(e), Left(es)) => Left(e :: es)
        case (Left(e), Right(_)) => Left(List(e))
        case (Right(_), Left(es)) => Left(es)
        case (Right(a), Right(as)) => Right(a :: as)
      }
    }

  }
}
