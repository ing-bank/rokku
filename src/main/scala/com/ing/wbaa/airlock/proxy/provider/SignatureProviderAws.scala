package com.ing.wbaa.airlock.proxy.provider

import akka.http.scaladsl.model.HttpRequest
import com.amazonaws.auth._
import com.ing.wbaa.airlock.proxy.data.{ AwsSecretKey, RequestId }
import com.ing.wbaa.airlock.proxy.handler.LoggerHandlerWithId
import com.ing.wbaa.airlock.proxy.provider.aws.SignatureHelpers

trait SignatureProviderAws extends SignatureHelpers {

  private val logger = new LoggerHandlerWithId

  def isUserAuthenticated(httpRequest: HttpRequest, awsSecretKey: AwsSecretKey)(implicit id: RequestId): Boolean = {
    val awsHeaders = getAWSHeaders(httpRequest)
    val credentials = new BasicAWSCredentials(awsHeaders.accessKey.getOrElse(""), awsSecretKey.value)
    val incomingRequest = getSignableRequest(httpRequest, awsHeaders.version)

    addHeadersToRequest(incomingRequest, awsHeaders, httpRequest.entity.contentType.mediaType.value)

    // generate new signature
    if (!credentials.getAWSAccessKeyId.isEmpty) {
      signS3Request(incomingRequest, credentials, awsHeaders.version, awsHeaders.signedHeadersMap.getOrElse("X-Amz-Date", ""))
      logger.debug("Signed Request:" + incomingRequest.getHeaders.toString)
    } else {
      logger.debug("Unable to create AWS signature to verify incoming request")
    }

    if (incomingRequest.getHeaders.containsKey("Authorization")) {
      val proxySignature = getSignatureFromAuthorization(incomingRequest.getHeaders.get("Authorization"))
      logger.debug(s"New Signature: $proxySignature Original Signature: ${awsHeaders.signature.getOrElse("")}")
      awsHeaders.signature.getOrElse("") == proxySignature
    } else false
  }

}
