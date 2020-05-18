package com.ing.wbaa.rokku.proxy.api

import java.util.concurrent.ForkJoinPool

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.get
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.ing.wbaa.rokku.proxy.config.StorageS3Settings
import com.ing.wbaa.rokku.proxy.data.HealthCheck
import com.ing.wbaa.rokku.proxy.data.HealthCheck.{ RGWListBuckets, S3ListBucket }
import com.ing.wbaa.rokku.proxy.provider.aws.S3Client
import org.scalatest.diagrams.Diagrams
import org.scalatest.flatspec.AnyFlatSpec

import scala.concurrent.ExecutionContext

class HealthServiceSpec extends AnyFlatSpec with ScalatestRouteTest with Diagrams {

  private trait HealthServiceMock extends HealthService with S3Client {
    override implicit def system: ActorSystem = ActorSystem.create("test-system")

    override protected[this] implicit def executionContext: ExecutionContext = system.dispatcher

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
  it should "return ok with concurrent requests" in new HealthServiceMock() {
    override def storageS3Settings: StorageS3Settings = new StorageS3Settings(system.settings.config) {
      override val hcMethod: HealthCheck.HCMethod = S3ListBucket
      override val hcInterval: Long = 0
    }
    override def listBucket: String = "some_bucket"

    private val threadPool = ExecutionContext.fromExecutor(new ForkJoinPool(32))

    private def runProbe() =
      new Runnable {
        override def run(): Unit = {
          Thread.sleep(100)
          getStatus(System.currentTimeMillis())
        }
      }

    for (_ <- 1 to 50) {
      for (_ <- 1 to 50) {
        threadPool.execute(runProbe())
      }
    }

    Thread.sleep(1000)
    val f = getStatus(System.currentTimeMillis())
    val route = for {
      r <- f
    } yield get { r.get }

    route.map(r =>
      Get() ~> r ~> check {
        assert(responseAs[String].contains("pong"))
      })

  }
}
