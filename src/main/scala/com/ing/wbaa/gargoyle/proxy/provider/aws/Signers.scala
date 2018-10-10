package com.ing.wbaa.gargoyle.proxy.provider.aws

import com.amazonaws.SignableRequest
import com.amazonaws.auth._
import com.amazonaws.services.s3.Headers
import com.amazonaws.services.s3.internal.RestUtils
import com.typesafe.scalalogging.LazyLogging

// we need custom class to override calculateContentHash.
// Instead of calculating hash on proxy we copy hash from client, otherwise we need to materialize body content
sealed class CustomV4Signer() extends AWS4Signer with LazyLogging {

  override def calculateContentHash(request: SignableRequest[_]): String = request.getHeaders.get("X-Amz-Content-SHA256")
  override def sign(request: SignableRequest[_], credentials: AWSCredentials): Unit = super.sign(request, credentials)
}

// we need custom class to override date of request. in V2 there is no direct method like in v4
sealed class CustomV2Signer(httpVerb: String, resourcePath: String, additionalQueryParamsToSign: java.util.Set[String] = null)
  extends AbstractAWSSigner with LazyLogging {

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

    // since we need to append resourcePath with queryParams we skip encodedResourcePath from original class
    // instead of generating new date here, we copy date from original request to avoid drift
    request.addHeader(Headers.DATE, request.getHeaders.get("Date"))

    val canonicalString = RestUtils.makeS3CanonicalString(httpVerb, resourcePath, request, null, additionalQueryParamsToSign)
    logger.debug("Calculated string to sign:\n\"" + canonicalString + "\"")

    val signature = super.signAndBase64Encode(canonicalString, sanitizedCredentials.getAWSSecretKey, SigningAlgorithm.HmacSHA1)

    request.addHeader("Authorization", "AWS " + sanitizedCredentials.getAWSAccessKeyId + ":" + signature)
  }
}
