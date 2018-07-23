package nl.wbaa.gargoyle.proxy.handler

import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{ HttpHeader, HttpRequest, RemoteAddress }
import nl.wbaa.gargoyle.proxy.providers.Secret

trait RequestHandler {
  val s3Host: String
  val s3Port: Int

  def validateUserRequest(request: HttpRequest, secret: Secret): Boolean = true

  /**
   * Translates a request from ingestion of the client towards what s3 expects.
   *   - Add forward header
   *   - Change authority to s3 host:port
   *
   * @param request incoming request on this server
   * @param remoteAddress originating client address
   * @return translated request for s3
   */
  def translateRequest(request: HttpRequest, remoteAddress: RemoteAddress): HttpRequest = {
    val headersIn: Seq[HttpHeader] =
      request.headers ++ List(
        RawHeader("X-Forwarded-For", remoteAddress.toOption.map(_.getHostAddress).getOrElse("unknown")),
        RawHeader("X-Forwarded-Proto", request._5.value)
      )
    request.copy(uri = request.uri.withAuthority(s3Host, s3Port), headers = headersIn.toList)
  }
}
