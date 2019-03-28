package com.ing.wbaa.airlock.proxy.api

import akka.Done
import akka.http.scaladsl.model._
import com.ing.wbaa.airlock.proxy.config.{ AtlasSettings, KafkaSettings }
import com.ing.wbaa.airlock.proxy.data.{ RequestId, S3Request, User }
import com.ing.wbaa.airlock.proxy.handler.LoggerHandlerWithId

import scala.concurrent.Future
import scala.util.Failure
import scala.util.matching.Regex

trait PostRequestActions {
  import PostRequestActions._

  import scala.concurrent.ExecutionContext.Implicits.global

  private val logger = new LoggerHandlerWithId

  protected[this] def atlasSettings: AtlasSettings

  protected[this] def kafkaSettings: KafkaSettings

  protected[this] def createLineageFromRequest(httpRequest: HttpRequest, userSTS: User, clientIPAddress: RemoteAddress)(implicit id: RequestId): Future[Done]

  protected[this] def emitEvent(s3Request: S3Request, method: HttpMethod, principalId: String)(implicit id: RequestId): Future[Done]

  protected[this] def setDefaultBucketAcl(bucketName: String): Future[Unit]

  private[this] def createAtlasLineage(response: HttpResponse, httpRequest: HttpRequest, userSTS: User, clientIPAddress: RemoteAddress)(implicit id: RequestId): Future[Done] =
    if (atlasSettings.atlasEnabled && kafkaSettings.kafkaEnabled && (response.status == StatusCodes.OK || response.status == StatusCodes.NoContent)) {
      // delete on AWS response 204
      logger.debug("Atlas integration enabled, about to create Lineage for the request")
      createLineageFromRequest(httpRequest, userSTS, clientIPAddress) map (_ => Done)
    } else {
      Future.successful(Done)
    }

  private[this] def createBucketNotification(response: HttpResponse, httpRequest: HttpRequest, s3Request: S3Request,
      userSTS: User)(implicit id: RequestId): Future[Done] =
    httpRequest.method match {
      case HttpMethods.POST | HttpMethods.PUT | HttpMethods.DELETE if kafkaSettings.kafkaEnabled && (response.status == StatusCodes.OK || response.status == StatusCodes.NoContent) =>
        emitEvent(s3Request, httpRequest.method, userSTS.userName.value)
      case _ => Future.successful(Done)
    }

  private[this] def updateBucketPermissions(httpRequest: HttpRequest, s3Request: S3Request): Future[Done] = {
    lazy val bucketName = bucketRegex findFirstMatchIn httpRequest.uri.path.toString() map (_.group(1))
    if (httpRequest.method == HttpMethods.PUT &&
      bucketName.isDefined) {
      setDefaultBucketAcl(bucketName.get) map (_ => Done)
    } else {
      Future.successful(Done)
    }
  }

  protected[this] def handlePostRequestActions(response: HttpResponse, httpRequest: HttpRequest, s3Request: S3Request, userSTS: User)(implicit id: RequestId): Unit = {
    val lineage = createAtlasLineage(response, httpRequest, userSTS, s3Request.clientIPAddress)
    val notification = createBucketNotification(response, httpRequest, s3Request, userSTS)
    val permissions = updateBucketPermissions(httpRequest, s3Request)

    // Set handlers to log errors
    lineage.andThen({
      case Failure(err) => logger.error(s"Error during lineage creation: ${err}")
    })
    notification.andThen({
      case Failure(err) => logger.error(s"Error while emitting bucket notification: ${err}")
    })
    permissions.andThen({
      case Failure(err) => logger.error(s"Error while setting bucket permissions: ${err}")
    })
  }
}

object PostRequestActions {
  private val bucketRegex: Regex = new Regex("^/([a-zA-Z0-9]+)/?$")
}
