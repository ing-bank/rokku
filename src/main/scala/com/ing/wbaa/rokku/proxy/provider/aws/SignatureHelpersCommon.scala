package com.ing.wbaa.rokku.proxy.provider.aws

import java.io.ByteArrayInputStream
import java.net.{ URI, URLDecoder }
import java.nio.charset.StandardCharsets
import java.util

import akka.http.scaladsl.model.HttpRequest
import com.amazonaws.DefaultRequest
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.http.HttpMethodName
import com.ing.wbaa.rokku.proxy.data.{ AWSHeaderValues, RequestId }
import com.ing.wbaa.rokku.proxy.handler.LoggerHandlerWithId

import scala.jdk.CollectionConverters._

trait SignatureHelpersCommon {
  private val logger = new LoggerHandlerWithId

  def extractRequestParameters(httpRequest: HttpRequest): util.Map[String, util.List[String]]

  def getAWSHeaders(httpRequest: HttpRequest): AWSHeaderValues

  def signS3Request(request: DefaultRequest[_], credentials: BasicAWSCredentials, date: String, region: String = "us-east-1")(implicit id: RequestId): Unit

  def addHeadersToRequest(request: DefaultRequest[_], awsHeaders: AWSHeaderValues, mediaType: String): Unit

  def getSignedHeaders(authorization: String): String

  final def cleanURLEncoding(param: String): String = URLDecoder.decode(param, StandardCharsets.UTF_8.toString)

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
          case _           => ("", List("").asJava)
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

    val requestParameters = extractRequestParameters(httpRequest)

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

object SignatureHelpersCommon {
  def extractHeaderOption(header: String)(implicit httpRequest: HttpRequest): Option[String] =
    if (httpRequest.getHeader(header).isPresent)
      Some(httpRequest.getHeader(header).get().value())
    else None

  val awsVersion: HttpRequest => SignatureHelpersCommon = { implicit request =>
    extractHeaderOption("authorization").map {
      case a if a.contains("AWS4") => new SignatureHelpersV4
      case a if a.contains("AWS")  => new SignatureHelpersV2
      case a                       => new NoSignerSupport(a)
    }.getOrElse {
      throw new Exception("Unable to determine AWS signature version")
    }
  }

}
