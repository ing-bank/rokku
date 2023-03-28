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

  def isUserAuthenticated(httpRequest: HttpRequest, awsSecretKey: AwsSecretKey)(implicit id: RequestId): Boolean = {
    val awsSignature = awsVersion(httpRequest)
    val awsHeaders = awsSignature.getAWSHeaders(httpRequest)

    val credentials = new BasicAWSCredentials(awsHeaders.accessKey.getOrElse(""), awsSecretKey.value)
    val incomingRequest = awsSignature.getSignableRequest(httpRequest)

    if (!credentials.getAWSAccessKeyId.isEmpty) {
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

  def isUserAuthenticated(httpRequest: HttpRequest, awsSecretKey: AwsSecretKey, s3Request: S3Request)(implicit id: RequestId): Boolean = {
    if (s3Request.presignParams.isEmpty) {
      isUserAuthenticated(httpRequest, awsSecretKey)
    } else {
      import scala.jdk.CollectionConverters._
      val awsSignature = awsVersion(s3Request.presignParams.get(X_AMZ_ALGORITHM))
      val credentials = new BasicSessionCredentials(s3Request.credential.accessKey.value, awsSecretKey.value, s3Request.credential.sessionToken.map(_.value).getOrElse(""))
      val incomingRequest = awsSignature.getSignableRequest(httpRequest)
      incomingRequest.setParameters(Map.empty[String, java.util.List[String]].asJava)
      awsSignature.presignS3Request(incomingRequest, credentials, s3Request.presignParams.get(X_AMZ_DATE), storageS3Settings.awsRegion)
      val orgSignature = s3Request.presignParams.get(X_AMZ_SIGNATURE)
      val newSignature = incomingRequest.getParameters.get(X_AMZ_SIGNATURE).get(0)
      orgSignature.equals(newSignature)
    }
  }
}
