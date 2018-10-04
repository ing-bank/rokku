package com.ing.wbaa.gargoyle.proxy.provider

import java.io.InputStream
import java.net.URI
import java.util

import akka.http.scaladsl.model.HttpRequest
import com.amazonaws.auth._
import com.amazonaws.http.HttpMethodName
import com.amazonaws.services.s3.Headers
import com.amazonaws.services.s3.internal.RestUtils
import com.amazonaws.util.DateUtils
import com.amazonaws.{ DefaultRequest, SignableRequest }
import com.ing.wbaa.gargoyle.proxy.data.AwsSecretKey
import com.typesafe.scalalogging.LazyLogging

private class customV4Signer() extends AWS4Signer with LazyLogging {

  override def calculateContentHash(request: SignableRequest[_]): String = {
    logger.debug("Calculating fake SHA256")
    request.getHeaders.get("X-Amz-Content-SHA256")
  }

  override def sign(request: SignableRequest[_], credentials: AWSCredentials): Unit = super.sign(request, credentials)

}

class customV2Signer(httpVerb: String, resourcePath: String, additionalQueryParamsToSign: java.util.Set[String] = null) extends AbstractAWSSigner with LazyLogging {

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

    //    val encodedResourcePath = SdkHttpUtils.appendUri(request.getEndpoint.getPath, SdkHttpUtils.urlEncode(resourcePath, true), true)

    // instead of generating new date here, we copy date from original request to avoid drift
    request.addHeader(Headers.DATE, request.getHeaders.get("Date"))

    //val additionalParams = Collections.unmodifiableSet(new java.util.HashSet[String](additionalQueryParamsToSign))

    println("parameters num: " + request.getParameters.size())

    val canonicalString = RestUtils.makeS3CanonicalString(httpVerb, resourcePath, request, null, additionalQueryParamsToSign)
    logger.debug("Calculated string to sign:\n\"" + canonicalString + "\"")

    val signature = super.signAndBase64Encode(canonicalString, sanitizedCredentials.getAWSSecretKey, SigningAlgorithm.HmacSHA1)

    request.addHeader("Authorization", "AWS " + sanitizedCredentials.getAWSAccessKeyId + ":" + signature)
  }
}

//todo: add extends AWS4Signer to class - not needed?
trait SignatureProviderAws extends LazyLogging {

  //lazy val signer = new AWS4Signer()

  //move to data
  case class AWSHeaderValues(accessKey: String, signedHeaders: String, signature: String, contentSHA256: String, requestDate: String, securityToken: String, version: String, contentMD5: String)

  private def decodeParam(param: String) = if (param.contains("%7E")) {
    logger.debug("cleaning " + param)
    param.replace("%7E", "~")
  } else { param }

  // Map[String, util.List[String]] is need by AWS4Signer
  private def extractRequestParameters(httpRequest: HttpRequest) = {
    import scala.collection.JavaConverters._

    val rawQueryString = httpRequest.uri.rawQueryString.getOrElse("")

    if (rawQueryString.length > 1) {
      rawQueryString match {
        // for aws subresource ?acl etc.
        case queryStr if queryStr.length > 1 && !queryStr.contains("=") =>
          Map(queryStr -> List.empty[String].asJava)
        // single param=value
        case queryStr if queryStr.contains("=") && !queryStr.contains("&") =>
          val params = queryStr.split("=").toList
          Map(params(0) -> List(decodeParam(params(1))).asJava)
        // multiple param=value
        case queryStr if queryStr.contains("&") =>
          queryStr.split("&").toList.map { param => // refactor this part
            param.split("=").toList
          }.map { paramValuePair =>
            if (paramValuePair.length == 1) {
              (paramValuePair(0), List[String]().asJava)
            } else {
              (paramValuePair(0), List(decodeParam(paramValuePair(1))).asJava)
            }
          }.toMap
        case _ => Map[String, java.util.List[String]]().empty
      }
    } else {
      Map[String, java.util.List[String]]().empty
    }
  }

  private def buildV2QueryParams(params: util.Set[String]) = {
    import scala.collection.JavaConverters._

    logger.debug("starting QueryParams")

    val signParams = List(
      "acl", "torrent", "logging", "location", "policy", "requestPayment", "versioning",
      "versions", "versionId", "notification", "uploadId", "uploads", "partNumber", "website",
      "delete", "lifecycle", "tagging", "cors", "restore", "replication", "accelerate",
      "inventory", "analytics", "metrics"
    )
    val queryParams = new StringBuilder("?")
    for (param <- params.asScala) {
      if (signParams.contains(param)) {
        logger.debug("adding QueryParams " + param)
        queryParams.append(param)
      }
    }
    logger.debug("created queryParams: " + queryParams.toString())
    queryParams.toString()
  }

  private def getSignature(authorization: String) = {
    if (authorization.contains("AWS4")) {
      """\S+ Signature=(\S+)""".r
        .findFirstMatchIn(authorization)
        .map(_ group 1).getOrElse("")
    } else {
      """AWS (\S+):(\S+)""".r
        .findFirstMatchIn(authorization)
        .map(_ group 2).getOrElse("")
    }
  }

  private def getCredential(authorization: String) = {
    if (authorization.contains("AWS4")) {
      """\S+ Credential=(\S+), """.r
        .findFirstMatchIn(authorization)
        .map(_ group 1).map(a => a.split("/").head).getOrElse("")

    } else {
      """AWS (\S+):\S+""".r
        .findFirstMatchIn(authorization)
        .map(_ group 1).getOrElse("")
    }
  }

  private def getAWSHeaders(httpRequest: HttpRequest) = {
    val authorization = httpRequest.getHeader("authorization").get().value()
    val signature = getSignature(authorization)

    val signedHeaders =
      """\S+ SignedHeaders=(\S+), """.r
        .findFirstMatchIn(authorization)
        .map(_ group 1).getOrElse("")

    val accessKey = getCredential(authorization)

    val version = if (authorization.contains("AWS4")) { "v4" } else { "v2" }
    val contentSHA256 = if (version == "v4") { httpRequest.getHeader("X-Amz-Content-SHA256").get().value() } else { "" }
    val requestDate = if (version == "v4") {
      httpRequest.getHeader("X-Amz-Date").get().value()
    } else {
      logger.debug("getting date from request v2 " + httpRequest.getHeader("Date").get().value())
      httpRequest.getHeader("Date").get().value()
    }
    val securityToken = httpRequest.getHeader("X-Amz-Security-Token").get().value()
    val contentMD5 = if (httpRequest.getHeader("Content-MD5").isPresent) {
      httpRequest.getHeader("Content-MD5").get().value()
    } else { "" }

    logger.debug("Original request headers: " + AWSHeaderValues(accessKey, signedHeaders, signature, contentSHA256, requestDate, securityToken, version, contentMD5))
    AWSHeaderValues(accessKey, signedHeaders, signature, contentSHA256, requestDate, securityToken, version, contentMD5)
  }

  private def getSignableRequest(httpRequest: HttpRequest, content: Option[InputStream] = None, request: DefaultRequest[_] = new DefaultRequest("s3")): DefaultRequest[_] = {
    import scala.collection.JavaConverters._

    // add values to Default request for signature
    request.setHttpMethod(httpRequest.method.value match {
      case "GET"    => HttpMethodName.GET
      case "POST"   => HttpMethodName.POST
      case "PUT"    => HttpMethodName.PUT
      case "DELETE" => HttpMethodName.DELETE
    })
    logger.debug("resource path " + httpRequest.uri.path.toString())
    request.setResourcePath(httpRequest.uri.path.toString())
    request.setEndpoint(
      new URI(s"http://${httpRequest.uri.authority.toString()}"
      ))
    if (!extractRequestParameters(httpRequest).asJava.isEmpty) {
      logger.debug("Setting additional params for request " + extractRequestParameters(httpRequest))
      request.setResourcePath(httpRequest.uri.path.toString())
      request.setParameters(extractRequestParameters(httpRequest).asJava)
    } else {
      request.setResourcePath(httpRequest.uri.path.toString())
    }
    content.map(c => request.setContent(c))

    request
  }

  private def signS3Request(request: DefaultRequest[_], cred: BasicAWSCredentials, version: String, date: String, region: String = "us-east-1"): Unit = {
    version match {
      case "v2" =>
        logger.debug("v2 params: " + request.getHttpMethod.toString + " " + request.getResourcePath)

        // add condition if contains = do not use buildV2
        val resourcePath = {
          import scala.collection.JavaConverters._

          logger.debug("vaules no " + request.getParameters.values().size())
          if (request.getParameters.values().size() > 0 && request.getParameters.values().asScala.head.isEmpty) {
            request.getResourcePath + buildV2QueryParams(request.getParameters.keySet())
          } else {
            request.getResourcePath
          }
        }
        logger.debug("query params " + request.getParameters.keySet())
        val singer = new customV2Signer(request.getHttpMethod.toString, resourcePath)
        singer.sign(request, cred)

      case "v4" =>
        val signer = new customV4Signer()
        signer.setRegionName(region)
        signer.setServiceName(request.getServiceName)
        signer.setOverrideDate(DateUtils.parseCompressedISO8601Date(date))
        signer.sign(request, cred)
    }
  }

  def isUserAuthenticated(httpRequest: HttpRequest, awsSecretKey: AwsSecretKey): Boolean = {
    val awsHeaders = getAWSHeaders(httpRequest)
    val credentials = new BasicAWSCredentials(awsHeaders.accessKey, awsSecretKey.value)
    val incomingRequest = getSignableRequest(httpRequest)

    //    val authorization = httpRequest.getHeader("authorization").get().value()

    // add headers from original request before sign
    incomingRequest.addHeader("X-Amz-Security-Token", awsHeaders.securityToken)
    if (!awsHeaders.contentSHA256.isEmpty) {
      //      incomingRequest.addHeader("SignedHeaders", awsHeaders.signedHeaders)
      incomingRequest.addHeader("X-Amz-Content-SHA256", awsHeaders.contentSHA256)
    }
    if (!awsHeaders.contentMD5.isEmpty) incomingRequest.addHeader("Content-MD5", awsHeaders.contentMD5)
    if (awsHeaders.version == "v2") {
      incomingRequest.addHeader("Content-Type", httpRequest.entity.contentType.mediaType.value)
      incomingRequest.addHeader("Date", awsHeaders.requestDate)
    }

    // generate new signature
    signS3Request(incomingRequest, credentials, awsHeaders.version, awsHeaders.requestDate)

    logger.debug("Signed Request:" + incomingRequest.getHeaders.toString)

    val newSignature = getSignature(incomingRequest.getHeaders.get("Authorization"))

    logger.debug(s"New Signature ${newSignature} Original Signature: ${awsHeaders.signature}")

    awsHeaders.signature == newSignature
  }

}
