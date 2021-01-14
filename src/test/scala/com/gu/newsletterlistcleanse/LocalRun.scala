package com.gu.newsletterlistcleanse

import scala.util.{Failure, Success, Try}

object LocalRun {

  def main(args: Array[String]): Unit = {
    println("Running the lambda locally")
    // full run
    //val input = GetCutOffDatesLambdaInput()
    // partial run
    val input = GetCutOffDatesLambdaInput(Array("Editorial_AnimalsFarmed"), dryRun = true)
    val lambda = new Lambda()

    // force the JVM to shutdown, some of our clients have a threadpool that isn't marked as `Daemon`.
    // This is fine as long as the `fork := true` setting is set in the sbt config
    Try(lambda.handler(input, null)) match {
      case Success(_) =>
        System.exit(0)
      case Failure(exception) =>
        exception.printStackTrace()
        System.exit(1)
    }
  }

}
