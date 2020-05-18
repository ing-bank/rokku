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
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

object HealthService {
  private def timestamp: Long = System.currentTimeMillis()

  private val statusMap = new ConcurrentHashMap[Long, StandardRoute]()

  private def clearStatus(): Unit = statusMap.clear()

  private def addStatus(probeResult: StandardRoute): StandardRoute = statusMap.put(timestamp, probeResult)

  private def getCurrentStatusMap: Future[mutable.Map[Long, StandardRoute]] = Future.successful(statusMap.asScala)

  private def getRouteStatus(implicit ec: ExecutionContext): Future[Option[StandardRoute]] = getCurrentStatusMap.map(_.headOption.map { case (_, r) => r })

}

trait HealthService extends RadosGatewayHandler with S3Client with LazyLogging {

  protected[this] implicit def executionContext: ExecutionContext

  import HealthService.{ addStatus, getCurrentStatusMap, clearStatus, getRouteStatus, timestamp }

  private lazy val interval = storageS3Settings.hcInterval

  private def updateStatus: Future[StandardRoute] = Future {
    clearStatus()
    storageS3Settings.hcMethod match {
      case RGWListBuckets => addStatus(execProbe(listAllBuckets _))
      case S3ListBucket   => addStatus(execProbe(listBucket _))
    }
  }
  private def updateStatusAndGet: Future[Option[StandardRoute]] =
    for {
      _ <- updateStatus
      s <- getRouteStatus
    } yield s

  def getStatus(currentTime: Long): Future[Option[StandardRoute]] =
    getCurrentStatusMap.flatMap(_ match {
      case m if m.isEmpty =>
        logger.debug("Status cache empty, running probe")
        updateStatusAndGet
      case m => m.keys.map {
        case entryTime if (entryTime + interval) < currentTime =>
          logger.debug("Status entry expired, renewing")
          updateStatusAndGet
        case _ =>
          logger.debug("Serving status from cache")
          Future.successful(m.map { case (_, r) => r }.headOption)
      }.head
    })

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
        onComplete(getStatus(timestamp)) {
          case Success(opt) => opt.getOrElse(complete(StatusCodes.InternalServerError -> "Failed to read status cache"))
          case Failure(e)   => complete(StatusCodes.InternalServerError -> "Failed to read status cache " + e.getMessage)
        }
      }
    }
}
