package com.ing.wbaa.gargoyle.proxy.provider

import akka.http.scaladsl.model.{ HttpMethod, HttpRequest }
import com.amazonaws.auth.{ AWS4Signer, BasicAWSCredentials }
import com.amazonaws.http.HttpMethodName
import com.amazonaws.{ DefaultRequest, SignableRequest }
import com.ing.wbaa.gargoyle.proxy.data.AwsSecretKey
import com.typesafe.scalalogging.LazyLogging

//todo: add extends AWS4Signer to class
trait SignatureProviderAws extends LazyLogging {

  //move to data
  case class AWSHeaderValues(signedHeaders: String, signature: String, contentSHA256: String, requestDate: String, securityToken: String)

  private def s3Request(service: String) = new DefaultRequest(service)

  //  private def parseParameters(httpRequest: HttpRequest) = {}

  private def getAWSHeaders(httpRequest: HttpRequest) = {

    //todo: Add conditions
    val signature = httpRequest.getHeader("Signature").get().value()
    val signedHeaders = httpRequest.getHeader("SignedHeaders").get().value()
    val contentSHA256 = httpRequest.getHeader("X-Amz-Content-SHA256").get().value()
    val requestDate = httpRequest.getHeader("X-Amz-Date").get().value()
    val securityToken = httpRequest.getHeader("X-Amz-Security-Token").get().value()

    AWSHeaderValues(signedHeaders, signature, contentSHA256, requestDate, securityToken)
  }

  private def getMethod(method: HttpMethod) = {
    method.value match {
      case "GET"    => HttpMethodName.GET
      case "POST"   => HttpMethodName.POST
      case "PUT"    => HttpMethodName.PUT
      case "DELETE" => HttpMethodName.DELETE
    }
  }

  def getSignableRequest(
      httpRequest: HttpRequest,
      request: DefaultRequest[_] = s3Request("s3")): SignableRequest[_] = {

    // add values to Default request for signature
    request.setHttpMethod(getMethod(httpRequest.method))
    //request.setEndpoint()

    request.setResourcePath(httpRequest.uri.path.toString())
    //request.setParameters() if not empty
    //request.setContent()

    request
  }

  def isUserAuthenticated(httpRequest: HttpRequest, awsSecretKey: AwsSecretKey): Boolean = {

    val signer = new AWS4Signer()

    val awsHeaders = getAWSHeaders(httpRequest)
    //todo: parase access key
    val credentials = new BasicAWSCredentials("VQ7pXnTHgkUWsdlCtOAFgSTIAxMUp3fS", awsSecretKey.value)

    val incomingRequest = getSignableRequest(httpRequest)

    // add headers before sign
    incomingRequest.addHeader("X-Amz-Security-Token", awsHeaders.securityToken)
    incomingRequest.addHeader("SignedHeaders", awsHeaders.signedHeaders)
    incomingRequest.addHeader("X-Amz-Content-SHA256", awsHeaders.contentSHA256)
    // generate new signature
    signer.sign(incomingRequest, credentials)

    logger.debug("signed request:" + incomingRequest.getHeaders.toString)

    logger.debug(s"new Signature ${incomingRequest.getHeaders.get("Signature")} original s: ${awsHeaders.signature}")
    awsHeaders.signature == incomingRequest.getHeaders.get("Signature")
  }

}
