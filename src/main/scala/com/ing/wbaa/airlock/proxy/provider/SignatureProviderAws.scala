package com.ing.wbaa.airlock.proxy.provider

import akka.http.scaladsl.model.HttpRequest
import com.amazonaws.auth._
import com.ing.wbaa.airlock.proxy.config.StorageS3Settings
import com.ing.wbaa.airlock.proxy.data.{ AwsSecretKey, RequestId }
import com.ing.wbaa.airlock.proxy.handler.LoggerHandlerWithId
import com.ing.wbaa.airlock.proxy.provider.aws.SignatureHelpersCommon.awsVersion

trait SignatureProviderAws {

  private val logger = new LoggerHandlerWithId
  protected[this] def storageS3Settings: StorageS3Settings

  def isUserAuthenticated(httpRequest: HttpRequest, awsSecretKey: AwsSecretKey)(implicit id: RequestId): Boolean = {
    val awsSignature = awsVersion(httpRequest)
    val awsHeaders = awsSignature.getAWSHeaders(httpRequest)

    val credentials = new BasicAWSCredentials(awsHeaders.accessKey.getOrElse(""), awsSecretKey.value)
    val incomingRequest = awsSignature.getSignableRequest(httpRequest, awsHeaders.version)

    if (!credentials.getAWSAccessKeyId.isEmpty) {
      awsSignature.addHeadersToRequest(incomingRequest, awsHeaders, httpRequest.entity.contentType.mediaType.value)
      awsSignature.signS3Request(incomingRequest, credentials, awsHeaders.version, awsHeaders.signedHeadersMap.getOrElse("X-Amz-Date", ""), storageS3Settings.awsRegion)
      logger.debug("Signed Request:" + incomingRequest.getHeaders.toString)
    }

    if (incomingRequest.getHeaders.containsKey("Authorization")) {
      val proxySignature = awsSignature.getSignatureFromAuthorization(incomingRequest.getHeaders.get("Authorization"))
      logger.debug(s"New Signature: $proxySignature Original Signature: ${awsHeaders.signature.getOrElse("")}")
      awsHeaders.signature.getOrElse("") == proxySignature
    } else false
  }

}
