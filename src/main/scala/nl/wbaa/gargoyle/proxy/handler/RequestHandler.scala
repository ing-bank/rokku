package nl.wbaa.gargoyle.proxy.handler

import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{ HttpHeader, HttpRequest, RemoteAddress }
import nl.wbaa.gargoyle.proxy.config.GargoyleStorageS3Settings
import nl.wbaa.gargoyle.proxy.providers.Secret

trait RequestHandler {

  val storageS3Settings: GargoyleStorageS3Settings

  def validateUserRequest(request: HttpRequest, secret: Secret): Boolean = true

  /**
   * Translates a request from ingestion of the client towards what s3 expects.
   *   - Add forward header
   *   - Change authority to s3 host:port
   *
   * @param request incoming request on this server
   * @param clientAddress originating client address
   * @return translated request for s3
   */
  def translateRequest(request: HttpRequest, clientAddress: RemoteAddress): HttpRequest = {
    val headersIn: Seq[HttpHeader] =
      request.headers ++ List(
        RawHeader("X-Forwarded-For", clientAddress.toOption.map(_.getHostAddress).getOrElse("unknown")),
        RawHeader("X-Forwarded-Proto", request._5.value)
      )

    request.copy(
      uri = request.uri.withAuthority(storageS3Settings.storageS3Host, storageS3Settings.storageS3Port),
      headers = headersIn.toList
    )
  }
}
