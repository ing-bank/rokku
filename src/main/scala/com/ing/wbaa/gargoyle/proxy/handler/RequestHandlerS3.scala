package com.ing.wbaa.gargoyle.proxy.handler

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model._
import com.ing.wbaa.gargoyle.proxy.config.GargoyleStorageS3Settings
import com.ing.wbaa.gargoyle.proxy.data.User
import com.ing.wbaa.gargoyle.proxy.handler.radosgw.RadosGatewayHandler
import com.ing.wbaa.gargoyle.proxy.provider.LineageProviderAtlas
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{ ExecutionContext, Future }

trait RequestHandlerS3 extends LazyLogging with RadosGatewayHandler with LineageProviderAtlas {

  protected[this] implicit def system: ActorSystem
  protected[this] implicit def executionContext: ExecutionContext

  protected[this] def storageS3Settings: GargoyleStorageS3Settings

  protected[this] def fireRequestToS3(request: HttpRequest, userSTS: User): Future[HttpResponse] = {
    logger.debug(s"Newly generated request: $request")
    val response = Http().singleRequest(request)
    response.foreach { r =>
      if (atlasSettings.atlasEnabled == true) {
        if (r.status == StatusCodes.OK) createLineageFromRequest(request, userSTS)
        if (r.status == StatusCodes.NoContent) createLineageFromRequest(request, userSTS) // delete on AWS response 204
      }
      logger.debug(s"Recieved response from Ceph: $r")
    }
    response
  }

  /**
   * Executes a request to S3.
   *
   * If we get back a Forbidden code, we can try to check if there's new credentials for Ceph first.
   * If so, we can retry the request.
   */
  protected[this] def executeRequest(request: HttpRequest, clientAddress: RemoteAddress, userSTS: User): Future[HttpResponse] = {
    val newRequest = translateRequest(request, clientAddress)

    fireRequestToS3(newRequest, userSTS).flatMap { response =>
      if (response.status == StatusCodes.Forbidden && handleUserCreationRadosGw(userSTS)) fireRequestToS3(newRequest, userSTS)
      else Future.successful(response)
    }
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
