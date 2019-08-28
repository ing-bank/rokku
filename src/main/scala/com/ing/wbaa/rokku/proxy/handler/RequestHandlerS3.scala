package com.ing.wbaa.rokku.proxy.handler

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import com.ing.wbaa.rokku.proxy.config.StorageS3Settings
import com.ing.wbaa.rokku.proxy.data.{ RequestId, S3Request, User }
import com.ing.wbaa.rokku.proxy.handler.exception.RokkuThrottlingException
import com.ing.wbaa.rokku.proxy.handler.radosgw.RadosGatewayHandler
import com.ing.wbaa.rokku.proxy.provider.aws.S3Client
import com.ing.wbaa.rokku.proxy.queue.UserRequestQueue

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Success

trait RequestHandlerS3 extends RadosGatewayHandler with S3Client with UserRequestQueue {

  private val logger = new LoggerHandlerWithId

  protected[this] implicit def system: ActorSystem

  protected[this] implicit def executionContext: ExecutionContext

  protected[this] def storageS3Settings: StorageS3Settings

  protected[this] def filterResponse(request: HttpRequest, userSTS: User, s3request: S3Request, response: HttpResponse)(implicit id: RequestId): HttpResponse

  /**
   * Updates the URI for S3 and sends the request to S3.
   *
   * If we get back a Forbidden code, we can try to check if there's new credentials for Ceph first.
   * If so, we can retry the request.
   */
  protected[this] def executeRequest(request: HttpRequest, userSTS: User, s3request: S3Request)(implicit id: RequestId): Future[HttpResponse] = {
    val userAgent = request.getHeader("User-Agent").orElse(RawHeader("User-Agent", "unknown")).value()
    val newRequest = request
      .withUri(request.uri.withAuthority(storageS3Settings.storageS3Authority))
      .withEntity(request.entity)
      .addHeader(RawHeader("User-Agent", userAgent))

    fireRequestToS3(newRequest, userSTS).flatMap { response =>
      if (response.status == StatusCodes.Forbidden && handleUserCreationRadosGw(userSTS))
        fireRequestToS3(newRequest, userSTS).flatMap(retryResponse => Future(filterResponse(request, userSTS, s3request, retryResponse)))
      else {
        Future(filterResponse(request, userSTS, s3request, response))
      }
    }
  }

  /**
   * Fire the request to S3.
   *
   * @param request request to fire to S3
   * @return response from S3
   */
  protected[this] def fireRequestToS3(request: HttpRequest)(implicit id: RequestId): Future[HttpResponse] = {
    logger.info(s"Request sent to Ceph: {}", request)
    Http()
      .singleRequest(request)
      .andThen { case Success(r) => logger.info(s"Received response from Ceph: {}", r.status) }
      .map(r => r.withEntity(r.entity.withoutSizeLimit()))
  }

  /**
   * Fire the request to S3 only if the user does not overflow the s3 backend queue and the queue is enabled
   *
   * @param request
   * @param user
   * @param id
   * @return response from s3 backend or http status TOO_MANY_REQUESTS
   */
  protected[this] def fireRequestToS3(request: HttpRequest, user: User)(implicit id: RequestId): Future[HttpResponse] = {
    if (storageS3Settings.isRequestUserQueueEnabled) {
      if (addIfAllowedUserToRequestQueue(user)) {
        fireRequestToS3(request).andThen { case _ => decrement(user) }
      } else {
        logger.info("user {} is sending too many requests", user.userName.value)
        Future.failed(new RokkuThrottlingException("Throttling"))
      }
    } else {
      fireRequestToS3(request)
    }
  }
}
