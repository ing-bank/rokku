package com.ing.wbaa.gargoyle.proxy.handler

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{ HttpHeader, HttpRequest, HttpResponse, RemoteAddress }
import com.ing.wbaa.gargoyle.proxy.config.GargoyleStorageS3Settings
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{ ExecutionContext, Future }

trait RequestHandlerS3 extends LazyLogging {

  protected[this] implicit def system: ActorSystem
  protected[this] implicit def executionContext: ExecutionContext

  protected[this] def storageS3Settings: GargoyleStorageS3Settings

  protected[this] def executeRequest(request: HttpRequest, clientAddress: RemoteAddress): Future[HttpResponse] = {
    val newRequest = translateRequest(request, clientAddress)

    logger.debug(s"Newly generated request: $newRequest")
    val response = Http().singleRequest(newRequest)
    response.foreach(r => logger.debug(s"Recieved response from Ceph: $r"))
    response
  }

  /**
   * Translates a request from ingestion of the client towards what s3 expects.
   *   - Add forward header
   *   - Change authority to s3 host:port
   *
   * @param request incoming request on this server
   * @param clientAddress originating client address
   * @return translated request for s3
   */
  protected[this] def translateRequest(request: HttpRequest, clientAddress: RemoteAddress): HttpRequest = {
    val headersIn: Seq[HttpHeader] =
      request.headers ++ List(
        RawHeader("X-Forwarded-For", clientAddress.toOption.map(_.getHostAddress).getOrElse("unknown")),
        RawHeader("X-Forwarded-Proto", request._5.value)
      )

    request.copy(
      uri = request.uri.withAuthority(storageS3Settings.storageS3Authority),
      headers = headersIn.toList
    )
  }
}
