package com.ing.wbaa.rokku.proxy.data

import akka.http.scaladsl.model.RemoteAddress.Unknown
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.model.{ HttpMethod, MediaType, MediaTypes, RemoteAddress }
import com.amazonaws.util.DateUtils
import com.ing.wbaa.rokku.proxy.handler.exception.RokkuPresignExpiredException
import com.ing.wbaa.rokku.proxy.provider.aws.SignatureHelpersCommon.{ X_AMZ_DATE, X_AMZ_EXPIRES }
import com.ing.wbaa.rokku.proxy.util.S3Utils
import com.typesafe.scalalogging.LazyLogging

/**
 * @param credential
 * @param s3BucketPath A None for bucket means this is an operation not targeted to a specific bucket (e.g. list buckets)
 * @param s3Object
 * @param accessType   The access type for this request, write includes actions like write/update/delete
 *
 */
case class S3Request(
    credential: AwsRequestCredential,
    s3BucketPath: Option[String],
    s3Object: Option[String],
    accessType: AccessType,
    clientIPAddress: RemoteAddress = Unknown,
    headerIPs: HeaderIPs = HeaderIPs(),
    mediaType: MediaType = MediaTypes.`text/plain`,
    presignParams: Option[Map[String, String]] = None
) {

  def userIps: UserIps = UserIps(clientIPAddress, headerIPs)

  def isPresign: Boolean = presignParams.isDefined

  def isNotPresign: Boolean = !isPresign

  def isPresignNotExpired: Boolean = {
    if (presignParams.isDefined) {
      val date = DateUtils.parseCompressedISO8601Date(presignParams.get(X_AMZ_DATE))
      val expiration = presignParams.get(X_AMZ_EXPIRES).toInt
      val isNotExpired = System.currentTimeMillis() <= date.getTime + expiration * 1000
      if (!isNotExpired) {
        throw new RokkuPresignExpiredException("presign request expired")
      }
      isNotExpired
    } else {
      throw new RokkuPresignExpiredException("it is not presign request")
    }
  }

}

object S3Request extends LazyLogging {

  def apply(credential: AwsRequestCredential, path: Path, httpMethod: HttpMethod,
      clientIPAddress: RemoteAddress, headerIPs: HeaderIPs, mediaType: MediaType, presignParams: Option[Map[String, String]]): S3Request = {

    val pathString = path.toString()
    val s3path = S3Utils.getS3PathWithoutBucketName(pathString)
    val s3Object = S3Utils.getS3FullObjectPath(pathString)

    val accessType = httpMethod.value match {
      case "GET"    => Read(httpMethod.value)
      case "HEAD"   => Head(httpMethod.value)
      case "PUT"    => Put(httpMethod.value)
      case "POST"   => Post(httpMethod.value)
      case "DELETE" => Delete(httpMethod.value)
      case _ =>
        logger.debug("HttpMethod not supported")
        NoAccess
    }

    S3Request(credential, s3path, s3Object, accessType, clientIPAddress, headerIPs, mediaType, presignParams)
  }
}
