package com.ing.wbaa.rokku.proxy.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ Route, StandardRoute }
import com.ing.wbaa.rokku.proxy.data.HealthCheck.{ RGWListBuckets, S3ListBucket }
import com.ing.wbaa.rokku.proxy.handler.radosgw.RadosGatewayHandler
import com.ing.wbaa.rokku.proxy.provider.aws.S3Client
import com.typesafe.scalalogging.LazyLogging

import scala.collection.mutable
import scala.util.{ Failure, Success, Try }

object HealthService {
  private def timestamp: Long = System.currentTimeMillis()
  private val statusMap = mutable.Map[Long, StandardRoute](System.currentTimeMillis() -> complete("pong"))

  private def clearStatus(): Unit = synchronized(statusMap.clear())
  private def addStatus(probeResult: StandardRoute): HealthService.statusMap.type = synchronized(statusMap += (timestamp -> probeResult))
  private def getCurrentStatus: mutable.Map[Long, StandardRoute] = statusMap
}

trait HealthService extends RadosGatewayHandler with S3Client with LazyLogging {
  import HealthService.{ addStatus, getCurrentStatus, clearStatus, timestamp }

  private lazy val interval = storageS3Settings.hcInterval

  private def updateStatus(): mutable.Map[Long, StandardRoute] = {
    clearStatus()
    storageS3Settings.hcMethod match {
      case RGWListBuckets => addStatus(execProbe(listAllBuckets _))
      case S3ListBucket   => addStatus(execProbe(listBucket _))
    }
  }

  private def getStatus(currentTime: Long): Option[StandardRoute] =
    getCurrentStatus.keys.map {
      case entryTime if (entryTime + interval) < currentTime =>
        logger.debug("Status entry expired, renewing")
        updateStatus().toMap.headOption.map { case (_, r) => r }
      case _ =>
        logger.debug("Serving status from cache")
        getCurrentStatus.headOption.map { case (_, r) => r }
    }.head

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
