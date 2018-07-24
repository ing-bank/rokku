package nl.wbaa.gargoyle.proxy
package api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.{ DiagrammedAssertions, FlatSpec }

class HealthServiceSpec extends FlatSpec with ScalatestRouteTest with DiagrammedAssertions {

  "A health service" should "respond to /ping with 'pong'" in new HealthService {
    Get("/ping") ~> route ~> check {
      assert(status == StatusCodes.OK)
      assert(responseAs[String] == "pong")
    }
  }
}
