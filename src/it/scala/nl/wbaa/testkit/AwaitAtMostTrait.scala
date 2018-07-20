package nl.wbaa.testkit

import scala.concurrent.duration._
import scala.util.Try


trait AwaitAtMostTrait {
  lazy val waitAtMostDuration: FiniteDuration = {
    val secondsTimeout = sys.env.get("INTEGRATION_TEST_TIMEOUT") match {
      case Some(timeout) => Try(timeout.toInt).toOption.getOrElse(40)
      case None          => 40
    }

    secondsTimeout.seconds
  }
}
