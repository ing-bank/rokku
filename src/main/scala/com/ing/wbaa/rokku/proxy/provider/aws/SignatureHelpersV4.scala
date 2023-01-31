package com.ing.wbaa.rokku.proxy.provider.aws

import java.util
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.RawHeader
import com.amazonaws.DefaultRequest
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.util.DateUtils
import com.ing.wbaa.rokku.proxy.provider.aws.SignatureHelpersCommon.extractHeaderOption
import com.ing.wbaa.rokku.proxy.data
import com.ing.wbaa.rokku.proxy.data.{ AWSHeaderValues, RequestId }
import com.ing.wbaa.rokku.proxy.handler.LoggerHandlerWithId

import scala.jdk.CollectionConverters._

class SignatureHelpersV4 extends SignatureHelpersCommon {
  private val logger = new LoggerHandlerWithId

  private def fixHeaderCapitals(header: String): String = {
    header.split("-").map { h =>
      s"${h(0).toUpper}${h.substring(1).toLowerCase}"
    }.mkString("-")
  }

  // java Map[String, util.List[String]] is need by AWS4Signer
  def extractRequestParameters(httpRequest: HttpRequest): util.Map[String, util.List[String]] = {
    val rawQueryString = httpRequest.uri.rawQueryString.getOrElse("")

    if (rawQueryString.length > 1) {
      rawQueryString match {
        // for aws subresource ?acl etc.
        case queryString if queryString.length > 1 && !queryString.contains("=") =>
          // aws uses subresource= during signature generation, so we add empty string to list - /demobucket/?acl="
          Map(queryString -> List[String]("").asJava).asJava

        // single param=value
        case queryString if queryString.contains("=") && !queryString.contains("&") => splitQueryToJavaMap(queryString)

        // multiple param=value
        case queryString if queryString.contains("&") => splitQueryToJavaMap(queryString)

        case _ => Map[String, java.util.List[String]]().empty.asJava
      }
    } else {
      Map[String, java.util.List[String]]().empty.asJava
    }
  }

  def getSignedHeaders(authorization: String): String =
    """\S+ SignedHeaders=(\S+), """.r
      .findFirstMatchIn(authorization)
      .map(_ group 1).getOrElse("")

  def getAWSHeaders(httpRequest: HttpRequest): AWSHeaderValues = {
    implicit val hr = httpRequest
    val authorization: Option[String] = extractHeaderOption("authorization")
    val signature = authorization.map(auth => getSignatureFromAuthorization(auth))
    val accessKey = authorization.map(auth => getCredentialFromAuthorization(auth))

    val signedHeadersMap = authorization.map(auth => getSignedHeaders(auth)).getOrElse("")
      .split(";")
      .toList
      .map { header =>
        if (header == "content-type") {
          (fixHeaderCapitals(header), httpRequest.entity.contentType.mediaType.value)
        } else if (header == "content-length") {
          val contentLength = httpRequest.entity.getContentLengthOption().orElse(0L)
          (fixHeaderCapitals(header), contentLength.toString)
        } else if (header == "amz-sdk-invocation-id" || header == "amz-sdk-retry") {
          (header, extractHeaderOption(header).getOrElse(""))
        } else if (header == "x-amz-content-sha256") {
          ("X-Amz-Content-SHA256", extractHeaderOption(header).getOrElse(""))
        } else {
          (fixHeaderCapitals(header), extractHeaderOption(header).getOrElse(""))
        }
      }.toMap

    data.AWSHeaderValues(accessKey, signedHeadersMap, signature, None, None, None)
  }

  // for now we do not have any regions, we use default one
  def signS3Request(request: DefaultRequest[_], credentials: BasicAWSCredentials, date: String, region: String = "us-east-1")(implicit id: RequestId): Unit = {
    logger.debug("Using version 4 signer")

    val signer = new CustomV4Signer()
    signer.setRegionName(region)
    signer.setServiceName(request.getServiceName)
    signer.setOverrideDate(DateUtils.parseCompressedISO8601Date(date))
    signer.sign(request, credentials)
  }

  // add headers from original request before sign
  def addHeadersToRequest(request: DefaultRequest[_], awsHeaders: AWSHeaderValues, mediaType: String): Unit =
    awsHeaders.signedHeadersMap.foreach(p => request.addHeader(p._1, p._2))

  override def setMinimalSignedHeaders(request: HttpRequest)(implicit id: RequestId): HttpRequest = {
    import scala.jdk.OptionConverters._
    val authHeaderName = "Authorization"
    val minSignedHeaders = "host;x-amz-content-sha256;x-amz-date"
    val authHeader = request.getHeader(authHeaderName).map(_.value()).toScala
    authHeader.map {
      auth =>
        val orgSignedHeaders = getSignedHeaders(auth)
        val newSignedHeaders = auth.replace(orgSignedHeaders, minSignedHeaders)
        val requestWithoutAuth = request.removeHeader(authHeaderName)
        requestWithoutAuth.addHeader(RawHeader(authHeaderName, newSignedHeaders))
    }.getOrElse(request)
  }
}
