package com.ing.wbaa.rokku.proxy.provider

import akka.http.scaladsl.model.HttpRequest
import com.amazonaws.auth._
import com.ing.wbaa.rokku.proxy.config.StorageS3Settings
import com.ing.wbaa.rokku.proxy.data.{ AwsSecretKey, RequestId, S3Request }
import com.ing.wbaa.rokku.proxy.handler.LoggerHandlerWithId
import com.ing.wbaa.rokku.proxy.provider.aws.SignatureHelpersCommon._

trait SignatureProviderAws {

  private val logger = new LoggerHandlerWithId

  protected[this] def storageS3Settings: StorageS3Settings

  def isUserAuthenticated(httpRequest: HttpRequest, awsSecretKey: AwsSecretKey, s3Request: S3Request)(implicit id: RequestId): Boolean = {
    if (s3Request.isNotPresign) {
      isUserAuthenticated(httpRequest, awsSecretKey)
    } else {
      isUserAuthenticatedPresignVer(httpRequest, awsSecretKey, s3Request)
    }
  }

  private def isUserAuthenticated(httpRequest: HttpRequest, awsSecretKey: AwsSecretKey)(implicit id: RequestId): Boolean = {
    val awsSignature = awsVersion(httpRequest)
    val awsHeaders = awsSignature.getAWSHeaders(httpRequest)

    val credentials = new BasicAWSCredentials(awsHeaders.accessKey.getOrElse(""), awsSecretKey.value)
    val incomingRequest = awsSignature.getSignableRequest(httpRequest)

    if (credentials.getAWSAccessKeyId.nonEmpty) {
      awsSignature.addHeadersToRequest(incomingRequest, awsHeaders, httpRequest.entity.contentType.mediaType.value)
      awsSignature.signS3Request(incomingRequest, credentials, awsHeaders.signedHeadersMap.getOrElse("X-Amz-Date", ""), storageS3Settings.awsRegion)
      logger.debug("Signed Request:" + incomingRequest.getHeaders.toString)
    }

    if (incomingRequest.getHeaders.containsKey("Authorization")) {
      val proxySignature = awsSignature.getSignatureFromAuthorization(incomingRequest.getHeaders.get("Authorization"))
      logger.debug(s"New Signature: $proxySignature Original Signature: ${awsHeaders.signature.getOrElse("")}")
      awsHeaders.signature.getOrElse("") == proxySignature
    } else false
  }

  private def isUserAuthenticatedPresignVer(httpRequest: HttpRequest, awsSecretKey: AwsSecretKey, s3Request: S3Request)(implicit id: RequestId) = {
    import scala.jdk.CollectionConverters._
    val awsSignature = awsVersion(s3Request.presignParams.get(X_AMZ_ALGORITHM))
    val credentials = new BasicSessionCredentials(s3Request.credential.accessKey.value, awsSecretKey.value, s3Request.credential.sessionToken.map(_.value).getOrElse(""))
    val incomingRequest = awsSignature.getSignableRequest(httpRequest)
    val additionalParams = incomingRequest.getParameters.asScala.filterNot(m => s3Request.presignParams.getOrElse(Map.empty).contains(m._1)).asJava
    incomingRequest.setParameters(additionalParams)
    awsSignature.presignS3Request(incomingRequest, credentials, s3Request.presignParams.get(X_AMZ_DATE), s3Request.presignParams.get(X_AMZ_EXPIRES).toInt, storageS3Settings.awsRegion)
    logger.debug("recalculated presign url={}, queryParams={}", incomingRequest, incomingRequest.getParameters)
    val orgSignature = s3Request.presignParams.get(X_AMZ_SIGNATURE)
    val newSignature = incomingRequest.getParameters.get(X_AMZ_SIGNATURE).get(0)
    logger.debug(s"New Signature: $newSignature Original Signature: $orgSignature")
    orgSignature.equals(newSignature) && s3Request.isPresignNotExpired
  }
}
