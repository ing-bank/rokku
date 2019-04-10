package com.ing.wbaa.airlock.proxy.provider

import akka.http.scaladsl.model.HttpRequest
import com.amazonaws.auth._
import com.ing.wbaa.airlock.proxy.config.StorageS3Settings
import com.ing.wbaa.airlock.proxy.data.{ AwsSecretKey, RequestId }
import com.ing.wbaa.airlock.proxy.handler.LoggerHandlerWithId
import com.ing.wbaa.airlock.proxy.provider.aws.{ SignatureHelpersV2, SignatureHelpersV4 }

trait SignatureProviderAws extends SignatureHelpersV4 with SignatureHelpersV2 {

  private val logger = new LoggerHandlerWithId
  protected[this] def storageS3Settings: StorageS3Settings

  def isUserAuthenticated(httpRequest: HttpRequest, awsSecretKey: AwsSecretKey)(implicit id: RequestId): Boolean = {
    val awsSignatureVersion = awsVersion(httpRequest)
    val awsHeaders =
      if (awsSignatureVersion == AWS_SIGN_V4) getAWSHeaders(httpRequest)
      else if (awsSignatureVersion == AWS_SIGN_V2 && storageS3Settings.v2SignatureEnabled)
        getAWSHeadersV2(httpRequest)
      else throw new Exception("Unsupported request, make sure that your client uses AWS V4 Signer")

    val credentials = new BasicAWSCredentials(awsHeaders.accessKey.getOrElse(""), awsSecretKey.value)
    val incomingRequest = getSignableRequest(httpRequest, awsHeaders.version)

    if (awsSignatureVersion == AWS_SIGN_V4 && !credentials.getAWSAccessKeyId.isEmpty) {
      addHeadersToRequest(incomingRequest, awsHeaders, httpRequest.entity.contentType.mediaType.value)
      signS3Request(incomingRequest, credentials, awsHeaders.version, awsHeaders.signedHeadersMap.getOrElse("X-Amz-Date", ""), storageS3Settings.awsRegion)
      logger.debug("Signed Request:" + incomingRequest.getHeaders.toString)

    } else if (awsSignatureVersion == AWS_SIGN_V2 && !credentials.getAWSAccessKeyId.isEmpty) {
      addHeadersToRequestV2(incomingRequest, awsHeaders, httpRequest.entity.contentType.mediaType.value)
      signS3RequestV2(incomingRequest, credentials, awsHeaders.version, awsHeaders.signedHeadersMap.getOrElse("X-Amz-Date", ""), storageS3Settings.awsRegion)
      logger.debug("Signed Request:" + incomingRequest.getHeaders.toString)

    } else
      logger.debug("Unable to create AWS signature to verify incoming request")

    if (incomingRequest.getHeaders.containsKey("Authorization")) {
      val proxySignature = getSignatureFromAuthorization(incomingRequest.getHeaders.get("Authorization"))
      logger.debug(s"New Signature: $proxySignature Original Signature: ${awsHeaders.signature.getOrElse("")}")
      awsHeaders.signature.getOrElse("") == proxySignature
    } else false
  }

}
