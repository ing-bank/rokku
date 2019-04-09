package com.ing.wbaa.airlock.proxy.provider.aws

import java.util

import akka.http.scaladsl.model.HttpRequest
import com.amazonaws.DefaultRequest
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.util.DateUtils
import com.ing.wbaa.airlock.proxy.data.AWSHeaderValues

import scala.collection.JavaConverters._

trait SignatureHelpersV4 extends SignatureHelpersCommon {

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
    implicit val ht = httpRequest
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

    AWSHeaderValues(accessKey, signedHeadersMap, signature, None, None, AWS_SIGN_V4, None)
  }

  // for now we do not have any regions, we use default one
  def signS3Request(request: DefaultRequest[_], credentials: BasicAWSCredentials, version: String, date: String, region: String = "us-east-1"): Unit = {
    val signer = new CustomV4Signer()
    signer.setRegionName(region)
    signer.setServiceName(request.getServiceName)
    signer.setOverrideDate(DateUtils.parseCompressedISO8601Date(date))
    signer.sign(request, credentials)
  }

  // add headers from original request before sign
  def addHeadersToRequest(request: DefaultRequest[_], awsHeaders: AWSHeaderValues, mediaType: String): Unit =
    awsHeaders.signedHeadersMap.foreach(p => request.addHeader(p._1, p._2))

}
