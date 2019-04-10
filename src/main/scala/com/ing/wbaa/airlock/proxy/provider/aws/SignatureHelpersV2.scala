package com.ing.wbaa.airlock.proxy.provider.aws

import java.util

import akka.http.scaladsl.model.HttpRequest
import com.amazonaws.DefaultRequest
import com.amazonaws.auth.BasicAWSCredentials
import com.ing.wbaa.airlock.proxy.data.{ AWSHeaderValues, RequestId }
import com.ing.wbaa.airlock.proxy.handler.LoggerHandlerWithId

import scala.collection.JavaConverters._

trait SignatureHelpersV2 extends SignatureHelpersCommon {
  private val logger = new LoggerHandlerWithId

  def extractRequestParametersV2(httpRequest: HttpRequest, version: String): util.Map[String, util.List[String]] = {
    val rawQueryString = httpRequest.uri.rawQueryString.getOrElse("")

    if (rawQueryString.length > 1) {
      rawQueryString match {
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

  def getAWSHeadersV2(httpRequest: HttpRequest): AWSHeaderValues = {
    implicit val hr = httpRequest

    val authorization: Option[String] = extractHeaderOption("authorization")

    val signature = authorization.map(auth => getSignatureFromAuthorization(auth))
    val accessKey = authorization.map(auth => getCredentialFromAuthorization(auth))

    val requestDate = extractHeaderOption("Date")
    val securityToken = extractHeaderOption("X-Amz-Security-Token")
    val contentMD5 = extractHeaderOption("Content-MD5")
    val possibleAWSHeaders = httpRequest.headers.filter(_.lowercaseName().contains("x-amz"))
      .map { h => (h.name(), h.value()) }.toMap

    AWSHeaderValues(accessKey, possibleAWSHeaders, signature, requestDate, securityToken, AWS_SIGN_V2, contentMD5)
  }

  // V2 is not using = after subresource
  private def buildQueryParamsV2(params: util.Set[String])(implicit id: RequestId): String = {
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

  // for now we do not have any regions, we use default one
  def signS3RequestV2(request: DefaultRequest[_], credentials: BasicAWSCredentials, version: String, date: String, region: String = "us-east-1")(implicit id: RequestId): Unit = {
    val requestParams = request.getParameters.values()

    val resourcePath = {
      // this is case where we need to append subresource to resourcePath
      // original S3Signer expects key=value params pair to parse
      if (requestParams.size() > 0 && requestParams.asScala.head.isEmpty) {
        val queryParams = buildQueryParamsV2(request.getParameters.keySet())
        request.getResourcePath + queryParams
      } else {
        request.getResourcePath
      }
    }
    val singer = new CustomV2Signer(request.getHttpMethod.toString, resourcePath)
    singer.sign(request, credentials)

  }

  // add headers from original request before sign
  def addHeadersToRequestV2(request: DefaultRequest[_], awsHeaders: AWSHeaderValues, mediaType: String): Unit = {
    request.addHeader("Content-Type", mediaType)
    awsHeaders.requestDate.foreach(date => request.addHeader("Date", date))
    awsHeaders.securityToken.foreach(token => request.addHeader("X-Amz-Security-Token", token))
    awsHeaders.contentMD5.foreach(contentMD5 => request.addHeader("Content-MD5", contentMD5))
  }
}
