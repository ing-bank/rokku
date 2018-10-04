package com.ing.wbaa.gargoyle.proxy.provider

import java.net.URI
import java.util

import akka.http.scaladsl.model.HttpRequest
import com.amazonaws.auth._
import com.amazonaws.http.HttpMethodName
import com.amazonaws.services.s3.Headers
import com.amazonaws.services.s3.internal.RestUtils
import com.amazonaws.util.DateUtils
import com.amazonaws.{ DefaultRequest, SignableRequest }
import com.ing.wbaa.gargoyle.proxy.data.{ AWSHeaderValues, AwsSecretKey }
import com.typesafe.scalalogging.LazyLogging

// we need custom class to override calculateContentHash.
// Instead of calculating hash on proxy we copy hash from client, otherwise we need to materialize body content
private sealed class CustomV4Signer() extends AWS4Signer with LazyLogging {

  override def calculateContentHash(request: SignableRequest[_]): String = request.getHeaders.get("X-Amz-Content-SHA256")
  override def sign(request: SignableRequest[_], credentials: AWSCredentials): Unit = super.sign(request, credentials)
}

// we need custom class to override date of request. in V2 there is no direct method like in v4
private sealed class CustomV2Signer(httpVerb: String, resourcePath: String, additionalQueryParamsToSign: java.util.Set[String] = null)
  extends AbstractAWSSigner with LazyLogging {

  override def addSessionCredentials(request: SignableRequest[_], credentials: AWSSessionCredentials): Unit =
    request.addHeader("x-amz-security-token", credentials.getSessionToken)

  override def sign(request: SignableRequest[_], credentials: AWSCredentials): Unit = {

    if (resourcePath == null) throw new UnsupportedOperationException("Cannot sign a request using a dummy S3Signer instance with " + "no resource path")
    if (credentials == null || credentials.getAWSSecretKey == null) {
      logger.debug("Canonical string will not be signed, as no AWS Secret Key was provided")
      return
    }
    val sanitizedCredentials = sanitizeCredentials(credentials)
    if (sanitizedCredentials.isInstanceOf[AWSSessionCredentials]) addSessionCredentials(request, sanitizedCredentials.asInstanceOf[AWSSessionCredentials])

    // since we need to append resourcePath with queryParams we skip encodedResourcePath from original class
    // instead of generating new date here, we copy date from original request to avoid drift
    request.addHeader(Headers.DATE, request.getHeaders.get("Date"))

    val canonicalString = RestUtils.makeS3CanonicalString(httpVerb, resourcePath, request, null, additionalQueryParamsToSign)
    logger.debug("Calculated string to sign:\n\"" + canonicalString + "\"")

    val signature = super.signAndBase64Encode(canonicalString, sanitizedCredentials.getAWSSecretKey, SigningAlgorithm.HmacSHA1)

    request.addHeader("Authorization", "AWS " + sanitizedCredentials.getAWSAccessKeyId + ":" + signature)
  }
}

trait SignatureProviderAws extends LazyLogging {

  //case class AWSHeaderValues(accessKey: String, signedHeaders: String, signature: String, contentSHA256: String, requestDate: String, securityToken: String, version: String, contentMD5: String)

  // we need to decode unsafe ASCII characters from hex. Some AWS parameters are encoded while reaching proxy
  private def cleanURLEncoding(param: String): String =
    if (param.contains("%7E")) {
      // uploadId parameter case
      param.replace("%7E", "~")
    } else { param }

  // java Map[String, util.List[String]] is need by AWS4Signer
  private def extractRequestParameters(httpRequest: HttpRequest): Map[String, util.List[String]] = {
    import scala.collection.JavaConverters._

    val rawQueryString = httpRequest.uri.rawQueryString.getOrElse("")

    if (rawQueryString.length > 1) {
      rawQueryString match {
        // for aws subresource ?acl etc.
        case queryString if queryString.length > 1 && !queryString.contains("=") =>
          Map(queryString -> List.empty[String].asJava)

        // single param=value
        case queryString if queryString.contains("=") && !queryString.contains("&") =>
          val params = queryString.split("=").toList
          Map(params(0) -> List(cleanURLEncoding(params(1))).asJava)

        // multiple param=value
        case queryString if queryString.contains("&") =>
          queryString.split("&").toList.map { param => //todo: refactor this part
            param.split("=").toList
          }.map { paramValuePair =>
            if (paramValuePair.length == 1) {
              (paramValuePair(0), List[String]().asJava)
            } else {
              (paramValuePair(0), List(cleanURLEncoding(paramValuePair(1))).asJava)
            }
          }.toMap
        case _ => Map[String, java.util.List[String]]().empty
      }
    } else {
      Map[String, java.util.List[String]]().empty
    }
  }

  private def buildV2QueryParams(params: util.Set[String]): String = {
    import scala.collection.JavaConverters._

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
  private def getSignatureFromAuthorization(authorization: String): String =
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
  private def getCredentialFromAuthorization(authorization: String): String =
    if (authorization.contains("AWS4")) {
      """\S+ Credential=(\S+), """.r
        .findFirstMatchIn(authorization)
        .map(_ group 1).map(a => a.split("/").head).getOrElse("")

    } else {
      """AWS (\S+):\S+""".r
        .findFirstMatchIn(authorization)
        .map(_ group 1).getOrElse("")
    }

  private def getSignedHeaders(authorization: String): String =
    """\S+ SignedHeaders=(\S+), """.r
      .findFirstMatchIn(authorization)
      .map(_ group 1).getOrElse("")

  private def getAWSHeaders(httpRequest: HttpRequest): AWSHeaderValues = {

    val authorization = httpRequest.getHeader("authorization").get().value()
    val version = if (authorization.contains("AWS4")) { "v4" } else { "v2" }
    val signature = getSignatureFromAuthorization(authorization)
    val accessKey = getCredentialFromAuthorization(authorization)
    // signed headers is the same for both versions
    val signedHeaders = getSignedHeaders(authorization)

    val securityToken = httpRequest.getHeader("X-Amz-Security-Token").get().value()

    val contentMD5: Option[String] =
      if (httpRequest.getHeader("Content-MD5").isPresent)
        Some(httpRequest.getHeader("Content-MD5").get().value())
      else None

    version match {
      case ver if ver == "v2" =>
        val requestDate = httpRequest.getHeader("Date").get().value()

        AWSHeaderValues(accessKey, signedHeaders, signature, None, requestDate, securityToken, version, contentMD5)

      case ver if ver == "v4" =>
        val contentSHA256 = httpRequest.getHeader("X-Amz-Content-SHA256").get().value()
        val requestDate = httpRequest.getHeader("X-Amz-Date").get().value()

        AWSHeaderValues(accessKey, signedHeaders, signature, Some(contentSHA256), requestDate, securityToken, version, contentMD5)
    }
  }

  private def getSignableRequest(
      httpRequest: HttpRequest,
      request: DefaultRequest[_] = new DefaultRequest("s3")): DefaultRequest[_] = {
    import scala.collection.JavaConverters._

    request.setHttpMethod(httpRequest.method.value match {
      case "GET"    => HttpMethodName.GET
      case "POST"   => HttpMethodName.POST
      case "PUT"    => HttpMethodName.PUT
      case "DELETE" => HttpMethodName.DELETE
    })

    request.setResourcePath(httpRequest.uri.path.toString())
    request.setEndpoint(new URI(s"http://${httpRequest.uri.authority.toString()}"))

    if (!extractRequestParameters(httpRequest).asJava.isEmpty) {
      val requestParameters = extractRequestParameters(httpRequest).asJava
      logger.debug(s"Setting additional params for request $requestParameters")

      request.setResourcePath(httpRequest.uri.path.toString())
      request.setParameters(requestParameters)
    } else {
      request.setResourcePath(httpRequest.uri.path.toString())
    }
    request
  }

  // for now we do not have any regions, we use default one
  private def signS3Request(request: DefaultRequest[_], credentials: BasicAWSCredentials, version: String, date: String, region: String = "us-east-1"): Unit = {
    version match {
      case "v2" =>
        val resourcePath = {
          import scala.collection.JavaConverters._

          val requestParams = request.getParameters.values()

          // this is case where we need to append subresource to resourcePath
          // original S3Signer expects key=value params pair to parse
          if (requestParams.size() > 0 && requestParams.asScala.head.isEmpty) {
            request.getResourcePath + buildV2QueryParams(request.getParameters.keySet())
          } else {
            request.getResourcePath
          }
        }
        val singer = new CustomV2Signer(request.getHttpMethod.toString, resourcePath)
        singer.sign(request, credentials)

      case "v4" =>
        val signer = new CustomV4Signer()
        signer.setRegionName(region)
        signer.setServiceName(request.getServiceName)
        signer.setOverrideDate(DateUtils.parseCompressedISO8601Date(date))
        signer.sign(request, credentials)
    }
  }

  def isUserAuthenticated(httpRequest: HttpRequest, awsSecretKey: AwsSecretKey): Boolean = {
    val awsHeaders = getAWSHeaders(httpRequest)
    val credentials = new BasicAWSCredentials(awsHeaders.accessKey, awsSecretKey.value)
    val incomingRequest = getSignableRequest(httpRequest)

    // add headers from original request before sign
    incomingRequest.addHeader("X-Amz-Security-Token", awsHeaders.securityToken)

    awsHeaders.contentSHA256.map(contentSHA256 => incomingRequest.addHeader("X-Amz-Content-SHA256", contentSHA256))
    awsHeaders.contentMD5.map(contentMD5 => incomingRequest.addHeader("Content-MD5", contentMD5))

    if (awsHeaders.version == "v2") {
      incomingRequest.addHeader("Content-Type", httpRequest.entity.contentType.mediaType.value)
      incomingRequest.addHeader("Date", awsHeaders.requestDate)
    }

    // generate new signature
    signS3Request(incomingRequest, credentials, awsHeaders.version, awsHeaders.requestDate)

    logger.debug("Signed Request:" + incomingRequest.getHeaders.toString)

    val newSignature = getSignatureFromAuthorization(incomingRequest.getHeaders.get("Authorization"))

    logger.debug(s"New Signature ${newSignature} Original Signature: ${awsHeaders.signature}")

    awsHeaders.signature == newSignature
  }

}
