package com.ing.wbaa.rokku.proxy.api

import java.util.concurrent.ForkJoinPool

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.ing.wbaa.rokku.proxy.config.StorageS3Settings
import com.ing.wbaa.rokku.proxy.provider.aws.S3Client
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.diagrams.Diagrams

import scala.concurrent.ExecutionContext

class HealthServiceMultiSpec extends AsyncFlatSpec with ScalatestRouteTest with Diagrams with HealthService with S3Client {

  override def storageS3Settings: StorageS3Settings = new StorageS3Settings(system.settings.config) {
    override val hcInterval = 10
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

  "A health service concurrent" should "respond to 'pong'" in {
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
