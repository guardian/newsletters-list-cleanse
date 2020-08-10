package com.gu.newsletterlistcleanse

import com.amazonaws.services.lambda.runtime.Context
import org.slf4j.{ Logger, LoggerFactory }

/**
 * This is compatible with aws' lambda JSON to POJO conversion.
 * You can test your lambda by sending it the following payload:
 * {"name": "Bob"}
 */
class UpdateBrazeUsersLambdaInput() {
  var name: String = _
  def getName(): String = name
  def setName(theName: String): Unit = name = theName
}

object UpdateBrazeUsersLambda {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  /*
   * This is your lambda entry point
   */
  def handler(lambdaInput: GetCleanseListLambdaInput, context: Context): Unit = {
    val env = Env()
    logger.info(s"Starting $env")
    logger.info(process(lambdaInput.name, env))
  }

  /*
   * I recommend to put your logic outside of the handler
   */
  def process(name: String, env: Env): String = s"Hello $name! (from ${env.app} in ${env.stack})\n"
}

object TestUpdateBrazeUsers {
  def main(args: Array[String]): Unit = {
    println(UpdateBrazeUsersLambda.process(args.headOption.getOrElse("Alex"), Env()))
  }
}
