package com.ing.wbaa.rokku.proxy.api

import akka.Done
import akka.http.scaladsl.model._
import com.ing.wbaa.rokku.proxy.data._
import com.ing.wbaa.rokku.proxy.handler.LoggerHandlerWithId
import com.ing.wbaa.rokku.proxy.handler.parsers.RequestParser.AWSRequestType
import com.ing.wbaa.rokku.proxy.metrics.MetricsFactory
import com.ing.wbaa.rokku.proxy.util.S3Utils
import com.typesafe.config.ConfigFactory

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Failure

trait PostRequestActions {

  protected[this] implicit def executionContext: ExecutionContext

  private val logger = new LoggerHandlerWithId

  private[this] def bucketNotificationEnabled = ConfigFactory.load().getBoolean("rokku.bucketNotificationEnabled")

  private[this] def atlasEnabled = ConfigFactory.load().getBoolean("rokku.atlas.enabled")

  protected[this] def createLineageFromRequest(httpRequest: HttpRequest, userSTS: User, userIPs: UserIps)(implicit id: RequestId): Future[Done]

  protected[this] def emitEvent(s3Request: S3Request, method: HttpMethod, principalId: String, awsRequest: AWSRequestType)(implicit id: RequestId): Future[Done]

  protected[this] def setDefaultBucketAclAndPolicy(bucketName: String)(implicit id: RequestId): Future[Unit]

  protected[this] def awsRequestFromRequest(request: HttpRequest): AWSRequestType

  private[this] def createAtlasLineage(response: HttpResponse, httpRequest: HttpRequest, userSTS: User, userIPs: UserIps)(implicit id: RequestId): Future[Done] =
    if (atlasEnabled && (response.status == StatusCodes.OK || response.status == StatusCodes.NoContent)) {
      // delete on AWS response 204
      logger.debug("Atlas integration enabled, about to create Lineage for the request")
      createLineageFromRequest(httpRequest, userSTS, userIPs) map (_ => Done)
    } else {
      Future.successful(Done)
    }

  private[this] def createBucketNotification(response: HttpResponse, httpRequest: HttpRequest, s3Request: S3Request,
      userSTS: User)(implicit id: RequestId): Future[Done] =
    httpRequest.method match {
      case HttpMethods.POST | HttpMethods.PUT | HttpMethods.DELETE if bucketNotificationEnabled && (response.status == StatusCodes.OK || response.status == StatusCodes.NoContent) =>
        MetricsFactory.incrementObjectsUploaded(httpRequest.method)
        emitEvent(s3Request, httpRequest.method, userSTS.userName.value, awsRequestFromRequest(httpRequest))
      case _ => Future.successful(Done)
    }

  private[this] def updateBucketPermissions(httpRequest: HttpRequest, s3Request: S3Request)(implicit id: RequestId): Future[Done] = {
    val fullPath = S3Utils.getPathNameFromUrlOrHost(httpRequest)
    val bucketName = S3Utils.getBucketName(fullPath)
    logger.debug("trying updateBucketPermissions for bucket={}, fullPath={}", bucketName, fullPath)
    val isPathOnlyWithBucketName = fullPath.split("/").length == 2
    if (httpRequest.method == HttpMethods.PUT && isPathOnlyWithBucketName) {
      setDefaultBucketAclAndPolicy(bucketName) map (_ => Done)
    } else {
      logger.debug("not create bucket command so updateBucketPermissions is not needed")
      Future.successful(Done)
    }
  }

  protected[this] def handlePostRequestActions(response: HttpResponse, httpRequest: HttpRequest, s3Request: S3Request, userSTS: User)(implicit id: RequestId): Unit = {
    val lineage = createAtlasLineage(response, httpRequest, userSTS, s3Request.userIps)
    val notification = createBucketNotification(response, httpRequest, s3Request, userSTS)
    val permissions = updateBucketPermissions(httpRequest, s3Request)

    // Set handlers to log errors
    lineage.andThen({
      case Failure(err) => logger.error(s"Error during lineage creation: $err")
    })
    notification.andThen({
      case Failure(err) => logger.error(s"Error while emitting bucket notification: $err")
    })
    permissions.andThen({
      case Failure(err) => logger.error(s"Error while setting bucket permissions: $err")
    })
  }
}
