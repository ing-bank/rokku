package com.ing.wbaa.gargoyle.proxy.data

import akka.http.scaladsl.model.{HttpMethod, HttpMethods}
import akka.http.scaladsl.model.Uri.Path
import com.typesafe.scalalogging.LazyLogging

/**
  * @param bucket A None for bucket means this is an operation not targeted to a specific bucket (e.g. list buckets)
  * @param accessType The access type for this request, write includes actions like write/update/delete
  */
case class S3Request(
                      credential: AwsRequestCredential,
                      bucket: Option[String],
                      accessType: AccessType,
)

object S3Request extends LazyLogging {
  def apply(credential: AwsRequestCredential, path: Path, httpMethod: HttpMethod): S3Request = {
    val bucket = path.toString
      .split("/")
      .toList
      .lift(1)

    val accessType = if (httpMethod == HttpMethods.GET) Read else Write

    S3Request(credential, bucket, accessType)
  }
}
