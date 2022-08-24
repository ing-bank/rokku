package com.ing.wbaa.rokku.proxy.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ Route, StandardRoute }
import com.ing.wbaa.rokku.proxy.data.HealthCheck.{ S3ListBucket, Default }
import com.ing.wbaa.rokku.proxy.data.RequestId
import com.ing.wbaa.rokku.proxy.handler.LoggerHandlerWithId
import com.ing.wbaa.rokku.proxy.provider.aws.S3Client

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters._
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

trait HealthService extends S3Client {

  private val logger = new LoggerHandlerWithId
  protected[this] implicit def executionContext: ExecutionContext

  import HealthService._

  private lazy val interval = storageS3Settings.hcInterval

  private def updateStatus(implicit id: RequestId): Future[StandardRoute] = Future {
    clearStatus()
    storageS3Settings.hcMethod match {
      case S3ListBucket => addStatus(execProbe(() => listBucket))
      case Default      => addStatus(execProbe(() => listBucket))
    }
  }
  private def updateStatusAndGet(implicit id: RequestId): Future[Option[StandardRoute]] =
    for {
      _ <- updateStatus
      s <- getRouteStatus
    } yield s

  def getStatus(currentTime: Long)(implicit id: RequestId): Future[Option[StandardRoute]] =
    getCurrentStatusMap.flatMap {
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
    }

  private def execProbe[A](p: () => A)(implicit id: RequestId): StandardRoute = {
    Try {
      p()
    } match {
      case Success(_) => complete("pong")
      case Failure(ex) =>
        implicit val returnStatusCode: StatusCodes.ServerError = StatusCodes.InternalServerError
        logger.error("storage status error {}", ex.getMessage)
        complete(StatusCodes.InternalServerError -> s"storage not available - $ex")
    }
  }

  final val healthRoute: Route =
    path("ping") {
      get {
        implicit val requestId: RequestId = RequestId(UUID.randomUUID().toString)
        onComplete(getStatus(timestamp)) {
          case Success(opt) =>
            opt.getOrElse(complete(StatusCodes.InternalServerError -> "Failed to read status cache"))
          case Failure(e) =>
            complete(StatusCodes.InternalServerError -> s"Failed to read status cache $e.getMessage")
        }
      }
    }
}
