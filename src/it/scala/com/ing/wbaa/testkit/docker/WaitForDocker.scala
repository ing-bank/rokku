package com.ing.wbaa.testkit.docker

import scala.concurrent.duration._
import scala.util.Try


object WaitForDocker {
  private[this] val maxWaitTimeForContainers: Int = 5 * 60

  val waitAtMostDuration: FiniteDuration =
    sys.env.get("INTEGRATION_TEST_TIMEOUT_SECONDS")
      .flatMap(t => Try(t.toInt).toOption)
      .getOrElse(maxWaitTimeForContainers)
      .seconds
}
