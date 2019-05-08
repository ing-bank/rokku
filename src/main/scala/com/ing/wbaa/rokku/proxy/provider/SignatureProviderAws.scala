package com.ing.wbaa.rokku.proxy.provider

import akka.http.scaladsl.model.HttpRequest
import com.amazonaws.auth._
import com.ing.wbaa.rokku.proxy.provider.aws.SignatureHelpersCommon.awsVersion
import com.ing.wbaa.rokku.proxy.config.StorageS3Settings
import com.ing.wbaa.rokku.proxy.data.{AwsSecretKey, RequestId}
import com.ing.wbaa.rokku.proxy.handler.LoggerHandlerWithId

trait SignatureProviderAws {

  private val logger = new LoggerHandlerWithId
  protected[this] def storageS3Settings: StorageS3Settings

  def isUserAuthenticated(httpRequest: HttpRequest, awsSecretKey: AwsSecretKey)(implicit id: RequestId): Boolean = {
    val awsSignature = awsVersion(httpRequest)
    val awsHeaders = awsSignature.getAWSHeaders(httpRequest)

    val credentials = new BasicAWSCredentials(awsHeaders.accessKey.getOrElse(""), awsSecretKey.value)
    val incomingRequest = awsSignature.getSignableRequest(httpRequest)

    if (!credentials.getAWSAccessKeyId.isEmpty) {
      awsSignature.addHeadersToRequest(incomingRequest, awsHeaders, httpRequest.entity.contentType.mediaType.value)
      awsSignature.signS3Request(
        incomingRequest,
        credentials,
        awsHeaders.signedHeadersMap.getOrElse("X-Amz-Date", ""),
        storageS3Settings.awsRegion
      )
      logger.debug("Signed Request:" + incomingRequest.getHeaders.toString)
    }

    if (incomingRequest.getHeaders.containsKey("Authorization")) {
      val proxySignature = awsSignature.getSignatureFromAuthorization(incomingRequest.getHeaders.get("Authorization"))
      logger.debug(s"New Signature: $proxySignature Original Signature: ${awsHeaders.signature.getOrElse("")}")
      awsHeaders.signature.getOrElse("") == proxySignature
    } else false
  }

}
