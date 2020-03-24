package com.ing.wbaa.rokku.proxy.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ Route, StandardRoute }
import com.ing.wbaa.rokku.proxy.data.HealthCheck.{ RGWListBuckets, S3ListBucket }
import com.ing.wbaa.rokku.proxy.handler.radosgw.RadosGatewayHandler
import com.ing.wbaa.rokku.proxy.provider.aws.S3Client
import com.typesafe.scalalogging.LazyLogging
import java.util.concurrent.ConcurrentHashMap
import scala.collection.JavaConverters._

import scala.collection.mutable
import scala.util.{ Failure, Success, Try }

object HealthService {
  private def timestamp: Long = System.currentTimeMillis()
  private val statusMap = new ConcurrentHashMap[Long, StandardRoute]()

  private def clearStatus(): Unit = statusMap.clear()
  private def addStatus(probeResult: StandardRoute): StandardRoute = statusMap.put(timestamp, probeResult)
  private def getCurrentStatus: mutable.Map[Long, StandardRoute] = statusMap.asScala
  private def getRouteStatus: Option[StandardRoute] = getCurrentStatus.headOption.map { case (_, r) => r }
}

trait HealthService extends RadosGatewayHandler with S3Client with LazyLogging {
  import HealthService.{ addStatus, getCurrentStatus, clearStatus, getRouteStatus, timestamp }

  private lazy val interval = storageS3Settings.hcInterval

  private def updateStatus(): StandardRoute = {
    clearStatus()
    storageS3Settings.hcMethod match {
      case RGWListBuckets => addStatus(execProbe(listAllBuckets _))
      case S3ListBucket   => addStatus(execProbe(listBucket _))
    }
  }

  private def getStatus(currentTime: Long): Option[StandardRoute] =
    getCurrentStatus match {
      case m if m.isEmpty =>
        logger.debug("Status cache empty, running probe")
        updateStatus()
        getRouteStatus
      case m => m.keys.map {
        case entryTime if (entryTime + interval) < currentTime =>
          logger.debug("Status entry expired, renewing")
          updateStatus()
          getRouteStatus
        case _ =>
          logger.debug("Serving status from cache")
          getRouteStatus
      }.head
    }

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
        getStatus(timestamp).getOrElse(complete(StatusCodes.InternalServerError -> "Failed to read status cache"))
      }
    }
}
