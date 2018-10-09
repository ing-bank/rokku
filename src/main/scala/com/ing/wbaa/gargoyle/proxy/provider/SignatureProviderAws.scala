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

  private final val AWS_SIGN_V2 = "v2"
  private final val AWS_SIGN_V4 = "v4"

  // we need to decode unsafe ASCII characters from hex. Some AWS parameters are encoded while reaching proxy
  private def cleanURLEncoding(param: String): String =
    if (param.contains("%7E")) {
      // uploadId parameter case
      param.replace("%7E", "~")
    } else { param }

  // java Map[String, util.List[String]] is need by AWS4Signer
  private def extractRequestParameters(httpRequest: HttpRequest, version: String): util.Map[String, util.List[String]] = {
    import scala.collection.JavaConverters._

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
        case queryString if queryString.contains("=") && !queryString.contains("&") =>
          queryString.split("=")
            .grouped(2)
            .map { case Array(k, v) =>
              Map(k -> List(cleanURLEncoding(v)).asJava).asJava
            }.toList.head

        // multiple param=value
        case queryString if queryString.contains("&") =>
          queryString.split("&").map { paramAndValue =>
            paramAndValue.split("=")
              .grouped(2)
              .map { case Array(k, v) =>
                (k, List(cleanURLEncoding(v)).asJava)
              }.toMap.asJava
          }.toList.head

        case _ => Map[String, java.util.List[String]]().empty.asJava
      }
    } else {
      Map[String, java.util.List[String]]().empty.asJava
    }
  }

  // V2 is not using = after subresource
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

    def extractHeaderOption(header: String): Option[String] =
      if (httpRequest.getHeader(header).isPresent)
        Some(httpRequest.getHeader(header).get().value())
      else None

    val authorization: Option[String] = extractHeaderOption("authorization")
    val version =
      authorization.map(auth => if (auth.contains("AWS4")) { AWS_SIGN_V4 } else { AWS_SIGN_V2 }).getOrElse("")
    val signature = authorization.map(auth => getSignatureFromAuthorization(auth))
    val accessKey = authorization.map(auth => getCredentialFromAuthorization(auth))
    // signed headers is the same for both versions
    val signedHeaders = authorization.map(auth => getSignedHeaders(auth))
    val securityToken = extractHeaderOption("X-Amz-Security-Token")
    val contentMD5: Option[String] = extractHeaderOption("Content-MD5")

    version match {
      case ver if ver == AWS_SIGN_V2 =>
        val requestDate =
          if (httpRequest.getHeader("Date").isPresent)
            Some(httpRequest.getHeader("Date").get().value())
          else None

        AWSHeaderValues(accessKey, signedHeaders, signature, None, requestDate, securityToken, version, contentMD5)

      case ver if ver == AWS_SIGN_V4 =>
        val contentSHA256 =
          if (httpRequest.getHeader("X-Amz-Content-SHA256").isPresent)
            Some(httpRequest.getHeader("X-Amz-Content-SHA256").get().value())
          else None
        val requestDate =
          if (httpRequest.getHeader("X-Amz-Date").isPresent)
            Some(httpRequest.getHeader("X-Amz-Date").get().value())
          else None

        AWSHeaderValues(accessKey, signedHeaders, signature, contentSHA256, requestDate, securityToken, version, contentMD5)
    }
  }

  private def getSignableRequest(
      httpRequest: HttpRequest,
      version: String,
      request: DefaultRequest[_] = new DefaultRequest("s3")): DefaultRequest[_] = {

    request.setHttpMethod(httpRequest.method.value match {
      case "GET"    => HttpMethodName.GET
      case "POST"   => HttpMethodName.POST
      case "PUT"    => HttpMethodName.PUT
      case "DELETE" => HttpMethodName.DELETE
    })

    request.setResourcePath(httpRequest.uri.path.toString())
    request.setEndpoint(new URI(s"http://${httpRequest.uri.authority.toString()}"))

    val requestParameters = extractRequestParameters(httpRequest, version)

    if (!requestParameters.isEmpty) {
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
    val requestParams = request.getParameters.values()

    version match {
      case AWS_SIGN_V2 =>
        val resourcePath = {
          import scala.collection.JavaConverters._

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

  def isUserAuthenticated(httpRequest: HttpRequest, awsSecretKey: AwsSecretKey): Boolean = {
    val awsHeaders = getAWSHeaders(httpRequest)
    val credentials = new BasicAWSCredentials(awsHeaders.accessKey.getOrElse(""), awsSecretKey.value)
    val incomingRequest = getSignableRequest(httpRequest, awsHeaders.version)

    // add headers from original request before sign
    incomingRequest.addHeader("X-Amz-Security-Token", awsHeaders.securityToken.getOrElse(""))

    awsHeaders.contentSHA256.foreach(contentSHA256 => incomingRequest.addHeader("X-Amz-Content-SHA256", contentSHA256))
    awsHeaders.contentMD5.foreach(contentMD5 => incomingRequest.addHeader("Content-MD5", contentMD5))

    if (awsHeaders.version == AWS_SIGN_V2) {
      incomingRequest.addHeader("Content-Type", httpRequest.entity.contentType.mediaType.value)
      awsHeaders.requestDate.foreach(date => incomingRequest.addHeader("Date", date))
    }

    // generate new signature
    if (!credentials.getAWSAccessKeyId.isEmpty) {
      signS3Request(incomingRequest, credentials, awsHeaders.version, awsHeaders.requestDate.getOrElse(""))
      logger.debug("Signed Request:" + incomingRequest.getHeaders.toString)
    } else {
      logger.debug("Unable to create AWS signature to verify incoming request")
    }

    val newSignature: Option[String] =
      if (incomingRequest.getHeaders.containsKey("Authorization")) {
        Some(getSignatureFromAuthorization(incomingRequest.getHeaders.get("Authorization")))
      } else None

    newSignature match {
      case Some(proxySignature) =>
        logger.debug(s"New Signature $proxySignature Original Signature: ${awsHeaders.signature.getOrElse("")}")
        awsHeaders.signature.getOrElse("") == proxySignature
      case _ => false
    }
  }

}
