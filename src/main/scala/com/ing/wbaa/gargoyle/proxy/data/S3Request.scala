package com.ing.wbaa.gargoyle.proxy.data

import com.ing.wbaa.gargoyle.proxy.data.AccessType.AccessType
import com.typesafe.scalalogging.LazyLogging

/**
  * @param bucket A None for bucket means this is an operation not targeted to a specific bucket (e.g. list buckets)
  * @param accessType The access type for this request, write includes actions like write/update/delete
  */
case class S3Request(
    accessKey: String,
    sessionToken: Option[String],
    bucket: Option[String],
    accessType: AccessType,
)

object S3Request extends LazyLogging {

  // TODO: extract actual info here!!
//  def apply(httpRequest: HttpRequest, credentials: Option[HttpCredentials]): S3Request = {
//
//  }
}
