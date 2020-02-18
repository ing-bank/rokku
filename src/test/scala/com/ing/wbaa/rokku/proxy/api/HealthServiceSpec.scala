package com.ing.wbaa.rokku.proxy.api

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.ing.wbaa.rokku.proxy.config.StorageS3Settings
import com.ing.wbaa.rokku.proxy.provider.aws.S3Client
import org.scalatest.{ DiagrammedAssertions, FlatSpec }

import scala.concurrent.ExecutionContext

class HealthServiceSpec extends FlatSpec with ScalatestRouteTest with DiagrammedAssertions {

  private trait HealthServiceMock extends HealthService with S3Client {
    override implicit def system: ActorSystem = ActorSystem.create("test-system")
    override implicit def executionContext: ExecutionContext = system.dispatcher
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
