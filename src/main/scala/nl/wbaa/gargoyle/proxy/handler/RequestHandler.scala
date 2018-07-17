package nl.wbaa.gargoyle.proxy.handler

import java.net.InetSocketAddress

import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{HttpHeader, HttpRequest}
import com.typesafe.config.ConfigFactory
import nl.wbaa.gargoyle.proxy.providers.Secret

trait RequestHandler {
  import RequestHandler._

  def validateUserRequest(request: HttpRequest, secret: Secret): Boolean = true
  def translateRequest(request: HttpRequest, remoteAddress: InetSocketAddress): HttpRequest = {
    val headersIn: Seq[HttpHeader] =
      request.headers :+ RawHeader("X-Forwarded-For", remoteAddress.getAddress.toString)

    request.copy(uri = request.uri.withAuthority(s3Host, s3Port), headers = headersIn.toList)
  }
}

object RequestHandler {
  private val configS3 = ConfigFactory.load().getConfig("s3.server")
  val s3Host: String = configS3.getString("host")
  val s3Port: Int = configS3.getInt("port")
}
