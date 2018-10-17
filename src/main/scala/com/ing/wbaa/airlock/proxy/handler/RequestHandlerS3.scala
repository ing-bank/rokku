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

  protected[this] def fireRequestToS3(request: HttpRequest): Future[HttpResponse] = {
    logger.debug(s"Request to send to Ceph: $request")
    Http().singleRequest(request)
      .andThen {
        case Success(r) => logger.debug(s"Recieved response from Ceph: ${r.status}")
      }
  }

  /**
   * Executes a request to S3.
   *
   * If we get back a Forbidden code, we can try to check if there's new credentials for Ceph first.
   * If so, we can retry the request.
   */
  protected[this] def executeRequest(request: HttpRequest, userSTS: User): Future[HttpResponse] = {
    fireRequestToS3(request).flatMap { response =>
      if (response.status == StatusCodes.Forbidden && handleUserCreationRadosGw(userSTS)) fireRequestToS3(request)
      else Future.successful(response)
    }
  }

  /**
   * Translates a request from ingestion of the client towards what s3 expects.
   *   - Add forward header
   *   - Change authority to s3 host:port
   *
   * @param request incoming request on this server
   * @param remoteAddressHeader Remote-Address header from the httpRequest
   * @param xForwardedForHeader X-Forwarded-For header from the httpRequest
   * @return translated request for s3
   */
  protected[this] def translateRequest(request: HttpRequest, remoteAddressHeader: Option[String], xForwardedForHeader: Option[String]): HttpRequest = {
    val newHeaders: Seq[HttpHeader] =
      request.headers
        .filter(h =>
          h.isNot("x-forwarded-for") && h.isNot("x-forwarded-proto")
        ) ++ List(
          RawHeader("X-Forwarded-For", xForwardedForHeader.getOrElse("") + ", " + remoteAddressHeader.map(_.split(":").head).getOrElse("unknown")),
          RawHeader("X-Forwarded-Proto", request._5.value)
        )

    request.copy(
      uri = request.uri.withAuthority(storageS3Settings.storageS3Authority),
      headers = newHeaders.toList
    )
  }
}
