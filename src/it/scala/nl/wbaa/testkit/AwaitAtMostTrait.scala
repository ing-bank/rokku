package nl.wbaa.testkit

import scala.concurrent.duration._
import scala.util.Try


trait AwaitAtMostTrait {
  private val maxWaitTimeForContainers: Int = 120

  lazy val waitAtMostDuration: FiniteDuration = {
    val secondsTimeout = sys.env.get("INTEGRATION_TEST_TIMEOUT") match {
      case Some(timeout) => Try(timeout.toInt).toOption.getOrElse(maxWaitTimeForContainers)
      case None          => maxWaitTimeForContainers
    }

    secondsTimeout.seconds
  }
}
