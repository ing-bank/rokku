package com.ing.wbaa.airlock.proxy.data

import akka.http.scaladsl.model.HttpMethod
import akka.http.scaladsl.model.Uri.Path
import com.typesafe.scalalogging.LazyLogging

/**
 * @param credential
 * @param bucket A None for bucket means this is an operation not targeted to a specific bucket (e.g. list buckets)
 * @param bucketObjectRoot
 * @param accessType The access type for this request, write includes actions like write/update/delete
 *
 */
case class S3Request(
    credential: AwsRequestCredential,
    bucket: Option[String],
    bucketObjectRoot: Option[String],
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

    val accessType = httpMethod.value match {
      case "GET"    => Read
      case "PUT"    => Write
      case "POST"   => Write
      case "DELETE" => Delete
      case _ =>
        logger.debug("HttpMetchod not supported")
        NoAccess
    }

    S3Request(credential, bucket, bucketObjectRoot, accessType)
  }
}
