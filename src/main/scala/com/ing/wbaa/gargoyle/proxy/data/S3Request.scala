package com.ing.wbaa.gargoyle.proxy.data

import akka.http.scaladsl.model.HttpRequest
import com.ing.wbaa.gargoyle.proxy.data.AccessType.AccessType

case class S3Request(
    accessKey: String,
    sessionToken: String,
    bucket: String,
    accessType: AccessType,
)

object S3Request {

  // TODO: extract actual info here!!
  def apply(httpRequest: HttpRequest) = {
    new S3Request("okAccessKey", "okSessionToken", "demobucket", AccessType.read)
  }
}
