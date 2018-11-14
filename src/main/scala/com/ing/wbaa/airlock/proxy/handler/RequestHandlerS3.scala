package com.ing.wbaa.airlock.proxy.handler

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import com.ing.wbaa.airlock.proxy.config.StorageS3Settings
import com.ing.wbaa.airlock.proxy.data.User
import com.ing.wbaa.airlock.proxy.handler.radosgw.RadosGatewayHandler
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Success

trait RequestHandlerS3 extends LazyLogging with RadosGatewayHandler {

  protected[this] implicit def system: ActorSystem
  protected[this] implicit def executionContext: ExecutionContext

  protected[this] def storageS3Settings: StorageS3Settings

  /**
   * Updates the URI for S3 and sends the request to S3.
   *
   * If we get back a Forbidden code, we can try to check if there's new credentials for Ceph first.
   * If so, we can retry the request.
   */
  protected[this] def executeRequest(request: HttpRequest, userSTS: User): Future[HttpResponse] = {
    val newRequest = request
      .withUri(request.uri.withAuthority(storageS3Settings.storageS3Authority))
      .withEntity(request.entity)
      .addHeader(RawHeader("User-Agent", request.getHeader("User-Agent").get().value()))

    fireRequestToS3(newRequest).flatMap { response =>
      if (response.status == StatusCodes.Forbidden && handleUserCreationRadosGw(userSTS)) fireRequestToS3(newRequest)
      else Future.successful(response)
    }
  }

  /**
   * Fire the request to S3.
   *
   * @param request request to fire to S3
   * @return response from S3
   */
  protected[this] def fireRequestToS3(request: HttpRequest): Future[HttpResponse] = {
    logger.debug(s"Request to send to Ceph: $request")
    Http()
      .singleRequest(request)
      .andThen { case Success(r) => logger.debug(s"Received response from Ceph: ${r.status}") }
  }
}
