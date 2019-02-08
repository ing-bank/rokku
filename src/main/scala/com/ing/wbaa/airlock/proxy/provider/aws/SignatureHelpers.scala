package com.ing.wbaa.airlock.proxy.provider.aws

import java.io.ByteArrayInputStream
import java.net.URI
import java.util

import akka.http.scaladsl.model.HttpRequest
import com.amazonaws.DefaultRequest
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.http.HttpMethodName
import com.amazonaws.util.DateUtils
import com.ing.wbaa.airlock.proxy.data.AWSHeaderValues
import com.typesafe.scalalogging.LazyLogging

import scala.collection.JavaConverters._

trait SignatureHelpers extends LazyLogging {

  final val AWS_SIGN_V2 = "v2"
  final val AWS_SIGN_V4 = "v4"

  // we need to decode unsafe ASCII characters from hex. Some AWS parameters are encoded while reaching proxy
  def cleanURLEncoding(param: String): String = {
    // uploadId parameter case
    param match {
      case p if p.nonEmpty && p.contains("%7E") => p.replace("%7E", "~")
      case p if p.nonEmpty && p.contains("%2F") => p.replace("%2F", "/")
      case p                                    => p
    }
  }

  // java Map[String, util.List[String]] is need by AWS4Signer
  def extractRequestParameters(httpRequest: HttpRequest, version: String): util.Map[String, util.List[String]] = {

    def splitQueryToJavaMap(queryString: String): util.Map[String, util.List[String]] =
      queryString.split("&").map { paramAndValue =>
        paramAndValue.split("=")
          .grouped(2)
          .map {
            case Array(k, v) => (k, List(cleanURLEncoding(v)).asJava)
            case Array(k)    => (k, List("").asJava)
          }
      }.toList.flatten.toMap.asJava

    val rawQueryString = httpRequest.uri.rawQueryString.getOrElse("")

    if (rawQueryString.length > 1) {
      rawQueryString match {
        // for aws subresource ?acl etc.
        case queryString if queryString.length > 1 && !queryString.contains("=") && version == AWS_SIGN_V4 =>
          // aws uses subresource= during signature generation, so we add empty string to list - /demobucket/?acl="
          Map(queryString -> List[String]("").asJava).asJava

        case queryString if queryString.length > 1 && !queryString.contains("=") && version == AWS_SIGN_V2 =>
          // v2 doesn't append = in signature - /demobucket/?acl"
          Map(queryString -> List.empty[String].asJava).asJava

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

  // V2 is not using = after subresource
  def buildV2QueryParams(params: util.Set[String]): String = {
    // list of allowed AWS subresource parameters
    val signParameters = List(
      "acl", "torrent", "logging", "location", "policy", "requestPayment", "versioning",
      "versions", "versionId", "notification", "uploadId", "uploads", "partNumber", "website",
      "delete", "lifecycle", "tagging", "cors", "restore", "replication", "accelerate",
      "inventory", "analytics", "metrics")

    val queryParams = new StringBuilder("?")

    for (param <- params.asScala) {
      if (signParameters.contains(param)) {
        queryParams.append(param)
      }
    }
    logger.debug("Created queryParams for V2 signature: " + queryParams.toString())
    queryParams.toString()
  }

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

  def getSignedHeaders(authorization: String): String =
    """\S+ SignedHeaders=(\S+), """.r
      .findFirstMatchIn(authorization)
      .map(_ group 1).getOrElse("")

  def getAWSHeaders(httpRequest: HttpRequest): AWSHeaderValues = {
    def extractHeaderOption(header: String): Option[String] =
      if (httpRequest.getHeader(header).isPresent)
        Some(httpRequest.getHeader(header).get().value())
      else None

    def fixHeaderCapitals(header: String): String = {
      header.split("-").map { h =>
        h(0).toUpper + h.substring(1).toLowerCase
      }.mkString("-")
    }

    val authorization: Option[String] = extractHeaderOption("authorization")

    val version =
      authorization.map(auth => if (auth.contains("AWS4")) {
        AWS_SIGN_V4
      } else {
        AWS_SIGN_V2
      }).getOrElse("")

    val signature = authorization.map(auth => getSignatureFromAuthorization(auth))
    val accessKey = authorization.map(auth => getCredentialFromAuthorization(auth))

    version match {
      case ver if ver == AWS_SIGN_V2 =>
        val requestDate = extractHeaderOption("Date")
        val securityToken = extractHeaderOption("X-Amz-Security-Token")
        val contentMD5 = extractHeaderOption("Content-MD5")
        val possibleAWSHeaders = httpRequest.headers.filter(_.lowercaseName().contains("x-amz"))
          .map { h => (h.name(), h.value()) }.toMap

        AWSHeaderValues(accessKey, possibleAWSHeaders, signature, requestDate, securityToken, AWS_SIGN_V2, contentMD5)

      case ver if ver == AWS_SIGN_V4 =>
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

        AWSHeaderValues(accessKey, signedHeadersMap, signature, None, None, version, None)
    }
  }

  private def getResourcePathFromRequest(httpRequest: HttpRequest): String =
    httpRequest.getHeader("Raw-Request-URI").isPresent match {
      case true  => httpRequest.getHeader("Raw-Request-URI").get().value().split("\\?").head
      case false => httpRequest.uri.path.toString()
    }

  def getSignableRequest(
      httpRequest: HttpRequest,
      version: String,
      request: DefaultRequest[_] = new DefaultRequest("s3")): DefaultRequest[_] = {

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

    val requestParameters = extractRequestParameters(httpRequest, version)

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

  // for now we do not have any regions, we use default one
  def signS3Request(request: DefaultRequest[_], credentials: BasicAWSCredentials, version: String, date: String, region: String = "us-east-1"): Unit = {
    val requestParams = request.getParameters.values()

    version match {
      case AWS_SIGN_V2 =>
        val resourcePath = {
          // this is case where we need to append subresource to resourcePath
          // original S3Signer expects key=value params pair to parse
          if (requestParams.size() > 0 && requestParams.asScala.head.isEmpty) {
            val queryParams = buildV2QueryParams(request.getParameters.keySet())
            request.getResourcePath + queryParams
          } else {
            request.getResourcePath
          }
        }
        val singer = new CustomV2Signer(request.getHttpMethod.toString, resourcePath)
        singer.sign(request, credentials)

      case AWS_SIGN_V4 =>
        val signer = new CustomV4Signer()
        signer.setRegionName(region)
        signer.setServiceName(request.getServiceName)
        signer.setOverrideDate(DateUtils.parseCompressedISO8601Date(date))
        signer.sign(request, credentials)
    }
  }

  // add headers from original request before sign
  def addHeadersToRequest(request: DefaultRequest[_], awsHeaders: AWSHeaderValues, mediaType: String): Unit = {
    // v4
    awsHeaders.signedHeadersMap.foreach(p => request.addHeader(p._1, p._2))
    // v2
    if (awsHeaders.version == AWS_SIGN_V2) {
      request.addHeader("Content-Type", mediaType)
      awsHeaders.requestDate.foreach(date => request.addHeader("Date", date))
      awsHeaders.securityToken.foreach(token => request.addHeader("X-Amz-Security-Token", token))
      awsHeaders.contentMD5.foreach(contentMD5 => request.addHeader("Content-MD5", contentMD5))
    }
  }

}
