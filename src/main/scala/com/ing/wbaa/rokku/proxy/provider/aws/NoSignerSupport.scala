package com.ing.wbaa.rokku.proxy.provider.aws

import java.util
import akka.http.scaladsl.model.HttpRequest
import com.amazonaws.DefaultRequest
import com.amazonaws.auth.{ AWSCredentials, BasicAWSCredentials }
import com.ing.wbaa.rokku.proxy.provider.aws.NoSignerSupport.SignatureNotSupported
import com.ing.wbaa.rokku.proxy.data.{ AWSHeaderValues, RequestId }

class NoSignerSupport(authorization: String) extends SignatureHelpersCommon {
  private val exceptionMsg = "Signature version not supported: " + authorization

  override def extractRequestParameters(httpRequest: HttpRequest): util.Map[String, util.List[String]] =
    throw new SignatureNotSupported(exceptionMsg)

  override def getAWSHeaders(httpRequest: HttpRequest): AWSHeaderValues = throw new SignatureNotSupported(exceptionMsg)

  override def addHeadersToRequest(request: DefaultRequest[_], awsHeaders: AWSHeaderValues, mediaType: String): Unit =
    throw new SignatureNotSupported(exceptionMsg)

  override def signS3Request(request: DefaultRequest[_], credentials: BasicAWSCredentials, date: String, region: String)(implicit id: RequestId): Unit =
    throw new SignatureNotSupported(exceptionMsg)

  def getSignedHeaders(authorization: String): String = throw new SignatureNotSupported(exceptionMsg)

  override def setMinimalSignedHeaders(request: HttpRequest)(implicit id: RequestId): HttpRequest = throw new SignatureNotSupported(exceptionMsg)

  def presignS3Request(request: DefaultRequest[_], credentials: AWSCredentials, date: String, expirationInSecond: Int, region: String)(implicit id: RequestId): Unit = new SignatureNotSupported(exceptionMsg)
}

object NoSignerSupport {
  class SignatureNotSupported(msg: String) extends Exception(msg)
}
