package com.ing.wbaa.airlock.proxy
package api

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.stream.{ ActorMaterializer, Materializer }
import com.ing.wbaa.airlock.proxy.config.StorageS3Settings
import org.scalatest.{ DiagrammedAssertions, FlatSpec }

import scala.concurrent.ExecutionContext

class HealthServiceSpec extends FlatSpec with ScalatestRouteTest with DiagrammedAssertions {

  private trait HealthServiceMock extends HealthService {
    override implicit def system: ActorSystem = ActorSystem.create("test-system")

    override implicit def executionContext: ExecutionContext = system.dispatcher

    implicit def materializer: Materializer = ActorMaterializer()
  }

  "A health service" should "respond to /ping with 'pong'" in new HealthServiceMock() {
    override protected[this] def storageS3Settings: StorageS3Settings = null
    override protected[this] def listAllBuckets: Seq[String] = List("a")
    Get("/ping") ~> healthRoute ~> check {
      assert(status == StatusCodes.OK)
      assert(responseAs[String] == "pong")
    }
  }

  "A health service" should "failed when storage returns an exception'" in new HealthServiceMock() {
    override protected[this] def storageS3Settings: StorageS3Settings = null
    override protected[this] def listAllBuckets: Seq[String] = throw new Exception()
    Get("/ping") ~> healthRoute ~> check {
      assert(status == StatusCodes.InternalServerError)
    }
  }
}
