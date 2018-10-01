package com.ing.wbaa.gargoyle.proxy.provider

import java.net.URI

import akka.http.scaladsl.model.HttpRequest
import com.amazonaws.DefaultRequest
import com.amazonaws.auth.{ AWS4Signer, BasicAWSCredentials }
import com.amazonaws.http.HttpMethodName
import com.ing.wbaa.gargoyle.proxy.data.AwsSecretKey
import com.typesafe.scalalogging.LazyLogging

//todo: add extends AWS4Signer to class - not needed?
trait SignatureProviderAws extends LazyLogging {

  lazy val signer = new AWS4Signer()

  //move to data
  case class AWSHeaderValues(accessKey: String, signedHeaders: String, signature: String, contentSHA256: String, requestDate: String, securityToken: String)

  private def extractRequestParameters(httpRequest: HttpRequest) = {
    import scala.collection.JavaConverters._

    val rawQueryString = httpRequest.uri.rawQueryString.getOrElse("")

    if (rawQueryString.length > 1) {
      rawQueryString match {
        // for aws subresource ?acl etc.
        case queryStr if queryStr.length > 1 && !queryStr.contains("=") =>
          Map(queryStr -> List[String]().asJava)
        // single param=value
        case queryStr if queryStr.contains("=") && !queryStr.contains("&") =>
          val params = queryStr.split("=").toList
          Map(params(0) -> List(params(1)).asJava)
        // multiple param=value
        case queryStr if queryStr.contains("&") =>
          queryStr.split("&").toList.map { param =>
            param.split("=").toList
          }.map { paramValuePair =>
            if (paramValuePair.length == 1) {
              (paramValuePair(0), List[String]().asJava) // check it possible in aws
            } else {
              (paramValuePair(0), List(paramValuePair(1)).asJava)
            }
          }.toMap
        case _ => Map[String, java.util.List[String]]().empty
      }
    } else {
      Map[String, java.util.List[String]]().empty
    }
  }

  private def getAWSHeaders(httpRequest: HttpRequest) = {
    val authorization = httpRequest.getHeader("authorization").get().value()
    val signature =
      """\S+ Signature=(\S+)""".r
        .findFirstMatchIn(authorization)
        .map(_ group 1)

    val signedHeaders =
      """\S+ SignedHeaders=(\S+), """.r
        .findFirstMatchIn(authorization)
        .map(_ group 1).getOrElse("")

    val accessKey =
      """AWS (\S+):\S+""".r
        .findFirstMatchIn(authorization)
        .map(_ group 1).getOrElse("")

    val contentSHA256 = httpRequest.getHeader("X-Amz-Content-SHA256").get().value()
    val requestDate = httpRequest.getHeader("X-Amz-Date").get().value()
    val securityToken = httpRequest.getHeader("X-Amz-Security-Token").get().value()

    logger.debug("DEBUG: request " + AWSHeaderValues(accessKey, signedHeaders, signature.getOrElse(""), contentSHA256, requestDate, securityToken))
    AWSHeaderValues(accessKey, signedHeaders, signature.getOrElse(""), contentSHA256, requestDate, securityToken)
  }

  private def getSignableRequest(httpRequest: HttpRequest, request: DefaultRequest[_] = new DefaultRequest("s3")): DefaultRequest[_] = {
    import scala.collection.JavaConverters._

    // add values to Default request for signature
    request.setHttpMethod(httpRequest.method.value match {
      case "GET"    => HttpMethodName.GET
      case "POST"   => HttpMethodName.POST
      case "PUT"    => HttpMethodName.PUT
      case "DELETE" => HttpMethodName.DELETE
    })
    request.setResourcePath(httpRequest.uri.path.toString())
    request.setEndpoint(new URI("http://" + httpRequest.uri.authority.toString()))
    if (extractRequestParameters(httpRequest).asJava.entrySet().size() >= 1) {
      request.setResourcePath(httpRequest.uri.path.toString())
      request.setParameters(extractRequestParameters(httpRequest).asJava)
    } else {
      request.setResourcePath(httpRequest.uri.path.toString())
    }
    request
  }

  private def signS3Request(request: DefaultRequest[_], cred: BasicAWSCredentials, region: String = "us-east-1"): Unit = {
    val signer = new AWS4Signer()
    signer.setRegionName(region)
    signer.setServiceName(request.getServiceName)
    signer.sign(request, cred)
  }

  def isUserAuthenticated(httpRequest: HttpRequest, awsSecretKey: AwsSecretKey): Boolean = {
    val awsHeaders = getAWSHeaders(httpRequest)
    val credentials = new BasicAWSCredentials(awsHeaders.accessKey, awsSecretKey.value)
    val incomingRequest = getSignableRequest(httpRequest)

    // add headers from original request before sign
    incomingRequest.addHeader("X-Amz-Security-Token", awsHeaders.securityToken)
    incomingRequest.addHeader("SignedHeaders", awsHeaders.signedHeaders)
    incomingRequest.addHeader("X-Amz-Content-SHA256", awsHeaders.contentSHA256)

    // generate new signature
    signS3Request(incomingRequest, credentials)

    logger.debug("Signed Request:" + incomingRequest.getHeaders.toString)

    val newSignature =
      """\S+ Signature=(\S+)""".r
        .findFirstMatchIn(httpRequest.getHeader("authorization").get().value())
        .map(_ group 1)

    logger.debug(s"New Signature ${newSignature.getOrElse("")} Original Signature: ${awsHeaders.signature}")

    awsHeaders.signature == newSignature.getOrElse("")
  }

}
