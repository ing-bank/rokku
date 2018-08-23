package com.ing.wbaa.gargoyle.proxy.data

import akka.http.scaladsl.model.HttpMethod
import akka.http.scaladsl.model.Uri.Path
import com.typesafe.scalalogging.LazyLogging

/**
 * @param credential
 * @param bucket A None for bucket means this is an operation not targeted to a specific bucket (e.g. list buckets)
 * @param bucketObject
 * @param accessType The access type for this request, write includes actions like write/update/delete
 *
 */
case class S3Request(
    credential: AwsRequestCredential,
    bucket: Option[String],
    bucketObject: Option[String],
    accessType: AccessType
)

object S3Request extends LazyLogging {
  def apply(credential: AwsRequestCredential, path: Path, httpMethod: HttpMethod): S3Request = {
    val bucket = path.toString
      .split("/")
      .toList
      .lift(1)

    val bucketObjectRoot = path.toString
      .split("/")
      .toList
      .lift(2)

    //    val accessType = if (httpMethod == HttpMethods.GET) Read else Write
    val accessType = httpMethod.value match {
      case "GET"    => Read
      case "PUT"    => Write
      case "POST"   => Write
      case "DELETE" => Delete
    }

    S3Request(credential, bucket, bucketObjectRoot, accessType)
  }
}
