package com.ing.wbaa.rokku.proxy.api

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.ing.wbaa.rokku.proxy.config.StorageS3Settings
import com.ing.wbaa.rokku.proxy.data.HealthCheck
import com.ing.wbaa.rokku.proxy.data.HealthCheck.{ RGWListBuckets, S3ListBucket }
import com.ing.wbaa.rokku.proxy.provider.aws.S3Client
import org.scalatest.diagrams.Diagrams
import org.scalatest.flatspec.AnyFlatSpec

class HealthServiceSpec extends AnyFlatSpec with ScalatestRouteTest with Diagrams {

  private trait HealthServiceMock extends HealthService with S3Client {
    override implicit def system: ActorSystem = ActorSystem.create("test-system")
    override def storageS3Settings: StorageS3Settings = StorageS3Settings(system)
  }

  "A health service" should "respond to /ping with 'pong' when probe is RGWListBuckets" in new HealthServiceMock() {
    override def storageS3Settings: StorageS3Settings = new StorageS3Settings(system.settings.config) {
      override val hcMethod: HealthCheck.HCMethod = RGWListBuckets
      override val hcInterval: Long = 0
    }
    override protected[this] def listAllBuckets: Seq[String] = List("b")
    Get("/ping") ~> healthRoute ~> check {
      assert(status == StatusCodes.OK)
      assert(responseAs[String] == "pong")
    }
  }

  it should "fail when storage returns an exception' when probe is RGWListBuckets" in new HealthServiceMock() {
    override def storageS3Settings: StorageS3Settings = new StorageS3Settings(system.settings.config) {
      override val hcMethod: HealthCheck.HCMethod = RGWListBuckets
      override val hcInterval: Long = 0
    }
    override protected[this] def listAllBuckets: Seq[String] = throw new Exception("RGW not available")
    Get("/ping") ~> healthRoute ~> check {
      assert(status == StatusCodes.InternalServerError)
    }
  }

  it should "respond to /ping with 'pong' when probe is S3ListBucket" in new HealthServiceMock() {
    override def storageS3Settings: StorageS3Settings = new StorageS3Settings(system.settings.config) {
      override val hcMethod: HealthCheck.HCMethod = S3ListBucket
      override val hcInterval: Long = 0
    }
    override def listBucket: String = "rokku_hc_bucket"
    Get("/ping") ~> healthRoute ~> check {
      assert(status == StatusCodes.OK)
      assert(responseAs[String] == "pong")
    }
  }

  it should "fail when storage returns an exception' when probe is S3ListBucket" in new HealthServiceMock() {
    override def storageS3Settings: StorageS3Settings = new StorageS3Settings(system.settings.config) {
      override val hcMethod: HealthCheck.HCMethod = S3ListBucket
      override val hcInterval: Long = 0
    }

    override def listBucket: String = throw new Exception("S3 not available")
    Get("/ping") ~> healthRoute ~> check {
      assert(status == StatusCodes.InternalServerError)
    }

  }
}
