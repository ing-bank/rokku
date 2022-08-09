package com.ing.wbaa.rokku.proxy.api

import akka.Done
import akka.http.scaladsl.model._
import com.ing.wbaa.rokku.proxy.data._
import com.ing.wbaa.rokku.proxy.handler.LoggerHandlerWithId
import com.ing.wbaa.rokku.proxy.handler.parsers.RequestParser.AWSRequestType
import com.ing.wbaa.rokku.proxy.metrics.MetricsFactory
import com.typesafe.config.ConfigFactory

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Failure

trait PostRequestActions {

  protected[this] implicit def executionContext: ExecutionContext

  private val logger = new LoggerHandlerWithId

  private[this] def bucketNotificationEnabled = ConfigFactory.load().getBoolean("rokku.bucketNotificationEnabled")

  protected[this] def emitEvent(s3Request: S3Request, method: HttpMethod, principalId: String, awsRequest: AWSRequestType)(implicit id: RequestId): Future[Done]

  protected[this] def setDefaultBucketAclAndPolicy(bucketName: String)(implicit id: RequestId): Future[Unit]

  protected[this] def awsRequestFromRequest(request: HttpRequest): AWSRequestType

  private[this] def createBucketNotification(response: HttpResponse, httpRequest: HttpRequest, s3Request: S3Request,
      userSTS: User)(implicit id: RequestId): Future[Done] =
    httpRequest.method match {
      case HttpMethods.POST | HttpMethods.PUT | HttpMethods.DELETE if bucketNotificationEnabled && (response.status == StatusCodes.OK || response.status == StatusCodes.NoContent) =>
        MetricsFactory.incrementObjectsUploaded(httpRequest.method)
        emitEvent(s3Request, httpRequest.method, userSTS.userName.value, awsRequestFromRequest(httpRequest))
      case _ => Future.successful(Done)
    }

  protected[this] def handlePostRequestActions(response: HttpResponse, httpRequest: HttpRequest, s3Request: S3Request, userSTS: User)(implicit id: RequestId): Unit = {
    val notification = createBucketNotification(response, httpRequest, s3Request, userSTS)

    // Set handlers to log errors
    notification.andThen({
      case Failure(err) => logger.error(s"Error while emitting bucket notification: $err")
    })
  }
}
