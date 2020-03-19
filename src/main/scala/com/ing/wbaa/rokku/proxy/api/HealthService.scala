package com.ing.wbaa.rokku.proxy.api

import java.util.concurrent.TimeUnit

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{ Route, StandardRoute }
import com.ing.wbaa.rokku.proxy.data.HealthCheck.{ RGWListBuckets, S3ListBucket }
import com.ing.wbaa.rokku.proxy.handler.radosgw.RadosGatewayHandler
import com.ing.wbaa.rokku.proxy.provider.aws.S3Client
import com.typesafe.scalalogging.LazyLogging

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.{ Duration, _ }
import scala.util.{ Failure, Success, Try }

trait HealthService extends RadosGatewayHandler with S3Client with LazyLogging {

  import akka.http.scaladsl.server.Directives._

  private val statusBuffer = new ListBuffer[StandardRoute]()

  lazy val startHCSchedule = system.scheduler.scheduleAtFixedRate(0.seconds, Duration(storageS3Settings.hcInterval, TimeUnit.MILLISECONDS)) {
    () =>
      statusBuffer.clear()
      storageS3Settings.hcMethod match {
        case RGWListBuckets => statusBuffer += execProbe(listAllBuckets _)
        case S3ListBucket   => statusBuffer += execProbe(listBucket _)
      }
  }(system.dispatcher)

  private def execProbe[A](p: () => A): StandardRoute =
    Try {
      p()
    } match {
      case Success(_)  => complete("pong")
      case Failure(ex) => complete(StatusCodes.InternalServerError -> s"storage not available - $ex")
    }

  final val healthRoute: Route =
    path("ping") {
      get {
        // we should start with assumption that S3 is ok and then run probe
        statusBuffer.toList.headOption.getOrElse(complete(StatusCodes.OK -> "pong"))
      }
    }
}
