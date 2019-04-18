package com.ing.wbaa.rokku.proxy.provider.aws

import com.amazonaws.SignableRequest
import com.amazonaws.auth.{ AWS4Signer, AWSCredentials }
import com.typesafe.scalalogging.LazyLogging

// we need custom class to override calculateContentHash.
// Instead of calculating hash on proxy we copy hash from client, otherwise we need to materialize body content
sealed class CustomV4Signer() extends AWS4Signer with LazyLogging {

  this.doubleUrlEncode = false
  override def calculateContentHash(request: SignableRequest[_]): String = request.getHeaders.get("X-Amz-Content-SHA256")
  override def sign(request: SignableRequest[_], credentials: AWSCredentials): Unit = super.sign(request, credentials)
}
