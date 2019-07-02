package com.ing.wbaa.rokku.proxy.persistence.serializers

import java.net.InetAddress

import akka.http.scaladsl.model.HttpHeader.ParsingResult
import akka.http.scaladsl.model.{HttpEntity, HttpHeader, HttpMethod, HttpMethods, HttpProtocol, HttpRequest, RemoteAddress, Uri}
import com.ing.wbaa.rokku.proxy.data.UserRawJson
import spray.json.DefaultJsonProtocol

import scala.collection.immutable

trait HttpRequestConversionSupport extends DefaultJsonProtocol {

  case class SimplifiedRemoteAddress(host: String) {
    def toRemoteAddr: RemoteAddress = {
      val a = host.split(":")
      RemoteAddress(InetAddress.getByName(a(0)), Some(a(1).toInt))
    }
  }

  case class SimplifiedHttpRequest(method: String, uri: String, headers: List[String], entity: String, httpProtocol: String)

  implicit val httpRequestF = jsonFormat5(SimplifiedHttpRequest)
  implicit val userSTSF = jsonFormat4(UserRawJson)
  implicit val remoteAddressF = jsonFormat1(SimplifiedRemoteAddress)

  private[persistence] def convertAkkaHeadersToStrings(headers: Seq[HttpHeader]): List[String] = headers.map(h => s"${h.name()}=${h.value()}").toList

  private def convertStringsToAkkaHeaders(headers: List[String]): immutable.Seq[HttpHeader] = headers.map { p =>
    val kv = p.split("=")
    HttpHeader.parse(kv(0), kv(1)) match {
      case ParsingResult.Ok(header, _) => header
      case ParsingResult.Error(error)  => throw new Exception(s"Unable to convert to HttpHeader: ${error.summary}")
    }
  }

  private def httpMethodFrom(m: String): HttpMethod = m match {
    case "GET"    => HttpMethods.GET
    case "HEAD"   => HttpMethods.HEAD
    case "PUT"    => HttpMethods.PUT
    case "POST"   => HttpMethods.POST
    case "DELETE" => HttpMethods.DELETE
  }

  private[persistence] def toAkkaHttpRequest(s: SimplifiedHttpRequest): HttpRequest =
    HttpRequest(
      httpMethodFrom(s.method),
      Uri(s.uri),
      convertStringsToAkkaHeaders(s.headers),
      HttpEntity(s.entity),
      HttpProtocol(s.httpProtocol)
    )
}
