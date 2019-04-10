package com.ing.wbaa.airlock.proxy.provider.aws

import java.io.ByteArrayInputStream
import java.net.URI
import java.util

import akka.http.scaladsl.model.HttpRequest
import com.amazonaws.DefaultRequest
import com.amazonaws.http.HttpMethodName
import com.ing.wbaa.airlock.proxy.data.RequestId
import com.ing.wbaa.airlock.proxy.handler.LoggerHandlerWithId

import scala.annotation.tailrec
import scala.collection.JavaConverters._

trait SignatureHelpersCommon {
  private val logger = new LoggerHandlerWithId
  final val AWS_SIGN_V2 = "v2"
  final val AWS_SIGN_V4 = "v4"

  protected[this] def extractRequestParameters(httpRequest: HttpRequest, version: String): util.Map[String, util.List[String]]

  protected[this] def extractRequestParametersV2(httpRequest: HttpRequest, version: String): util.Map[String, util.List[String]]

  private val asciiEncodingTable = Map(
    "%2A" -> "*",
    "%2B" -> "+",
    "%2F" -> "/",
    "%3C" -> "<",
    "%3E" -> ">",
    "%3F" -> "?",
    "%5B" -> "[",
    "%5D" -> "]",
    "%7E" -> "~",
    "%7B" -> "{",
    "%7D" -> "}",
    "%20" -> " ",
    "%21" -> "!",
    "%24" -> "$",
    "%23" -> "#",
    "%24" -> "$",
    "%26" -> "&",
    "%28" -> "(",
    "%29" -> ")"
  )

  // we need to decode unsafe ASCII characters from hex. Some AWS parameters are encoded while reaching proxy
  @tailrec
  final def cleanURLEncoding(param: String, utfCodes: List[String] = asciiEncodingTable.keys.toList): String =
    utfCodes match {
      case h :: t =>
        val newParam = param.replace(h, asciiEncodingTable.get(h).get)
        cleanURLEncoding(newParam, t)
      case Nil => param
    }

  val awsVersion: HttpRequest => String = { implicit request =>
    extractHeaderOption("authorization").map(auth => if (auth.contains("AWS4")) {
      AWS_SIGN_V4
    } else {
      AWS_SIGN_V2
    }).getOrElse("")
  }

  def extractHeaderOption(header: String)(implicit httpRequest: HttpRequest): Option[String] =
    if (httpRequest.getHeader(header).isPresent)
      Some(httpRequest.getHeader(header).get().value())
    else None

  // we have different extract pattern for V2 and V4
  def getSignatureFromAuthorization(authorization: String): String =
    if (authorization.contains("AWS4")) {
      """\S+ Signature=(\S+)""".r
        .findFirstMatchIn(authorization)
        .map(_ group 1).getOrElse("")
    } else {
      """AWS (\S+):(\S+)""".r
        .findFirstMatchIn(authorization)
        .map(_ group 2).getOrElse("")
    }

  def splitQueryToJavaMap(queryString: String): util.Map[String, util.List[String]] =
    queryString.split("&").map { paramAndValue =>
      paramAndValue.split("=")
        .grouped(2)
        .map {
          case Array(k, v) => (k, List(cleanURLEncoding(v)).asJava)
          case Array(k)    => (k, List("").asJava)
        }
    }.toList.flatten.toMap.asJava

  // we have different extract pattern for V2 and V4
  def getCredentialFromAuthorization(authorization: String): String =
    if (authorization.contains("AWS4")) {
      """\S+ Credential=(\S+), """.r
        .findFirstMatchIn(authorization)
        .map(_ group 1).map(a => a.split("/").head).getOrElse("")

    } else {
      """AWS (\S+):\S+""".r
        .findFirstMatchIn(authorization)
        .map(_ group 1).getOrElse("")
    }

  private def getResourcePathFromRequest(httpRequest: HttpRequest): String =
    httpRequest.getHeader("Raw-Request-URI").isPresent match {
      case true  => httpRequest.getHeader("Raw-Request-URI").get().value().split("\\?").head
      case false => httpRequest.uri.path.toString()
    }

  def getSignableRequest(
      httpRequest: HttpRequest,
      version: String,
      request: DefaultRequest[_] = new DefaultRequest("s3"))(implicit id: RequestId): DefaultRequest[_] = {

    request.setHttpMethod(httpRequest.method.value match {
      case "GET"    => HttpMethodName.GET
      case "POST"   => HttpMethodName.POST
      case "PUT"    => HttpMethodName.PUT
      case "DELETE" => HttpMethodName.DELETE
      case "HEAD"   => HttpMethodName.HEAD
      case _        => throw new Exception("Method not supported, request signature verification failed")
    })

    val resourcePath = getResourcePathFromRequest(httpRequest)

    request.setResourcePath(resourcePath)
    request.setEndpoint(new URI(s"http://${httpRequest.uri.authority.toString()}"))

    val requestParameters =
      if (version == AWS_SIGN_V4) extractRequestParameters(httpRequest, version)
      else extractRequestParametersV2(httpRequest, version)

    httpRequest.method.value match {
      case "POST" if !requestParameters.isEmpty =>
        logger.debug(s"Setting additional params (as body) for request $requestParameters")
        val requestParamsCombined =
          requestParameters.asScala.map {
            case (k, v) if v.isEmpty => s"$k="
            case (k, v)              => s"$k=${v.asScala.head}"
          }.mkString("&")

        request.setResourcePath(resourcePath)
        request.setParameters(requestParameters)
        request.setContent(new ByteArrayInputStream(requestParamsCombined.getBytes()))

      case _ if !requestParameters.isEmpty =>
        logger.debug(s"Setting additional params for request $requestParameters")

        request.setResourcePath(resourcePath)
        request.setParameters(requestParameters)

      case _ =>
        request.setResourcePath(resourcePath)
    }
    request
  }

}
