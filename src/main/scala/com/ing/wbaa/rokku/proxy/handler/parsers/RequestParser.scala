package com.ing.wbaa.rokku.proxy.handler.parsers

import akka.http.scaladsl.model.{ HttpMethods, HttpRequest, MediaTypes }
import com.ing.wbaa.rokku.proxy.handler.parsers.RequestParser._
import com.ing.wbaa.rokku.proxy.util.S3Utils

object RequestParser {

  sealed trait AWSRequestType
  sealed trait AWSMetaRequestType extends AWSRequestType

  case class RequestTypeUnknown() extends AWSRequestType

  case class MultipartRequestType(uploadId: String, completeMultipartUpload: Boolean) extends AWSRequestType

  //
  class ModifyObjectRequestType() extends AWSRequestType

  case class GetObjectRequestType() extends AWSRequestType

  case class ListObjectsRequestType() extends AWSRequestType

  case class PutObjectRequestType() extends ModifyObjectRequestType

  case class PostObjectRequestType() extends ModifyObjectRequestType

  case class DeleteObjectRequestType() extends ModifyObjectRequestType

  case class ListBucketsRequestType() extends AWSRequestType

  case class BucketOpsRequestType() extends AWSRequestType

  case class ObjectOpsRequestType() extends AWSRequestType

  case class SubFolderOpsRequestType() extends AWSRequestType

  case class MultiDeleteRequestType() extends ModifyObjectRequestType

  case class HeadObjectRequestType() extends ModifyObjectRequestType

  case class HeadBucketRequestType() extends AWSRequestType
}

trait RequestParser {

  private def uploadId(queryString: String) = queryString
    .split("&")
    .filter(_.contains("uploadId")).flatMap(_.split("=")).toList(1)

  def awsRequestFromRequest(request: HttpRequest): AWSRequestType = {
    val HttpRequest(method, uri, _, _, _) = request
    val queryString = uri.queryString().getOrElse("")
    val containsUploadId = queryString.contains("uploadId")
    val isXML = request.entity.contentType.mediaType == MediaTypes.`application/xml`
    val isOctetStream = request.entity.contentType.mediaType == MediaTypes.`application/octet-stream`

    // the same as in S3Request
    val pathString = request.uri.path.toString()
    val s3path = S3Utils.getS3PathWithoutBucketName(pathString)
    val s3Object = S3Utils.getS3FullObjectPath(pathString)
    //    val s3Bucket = S3Utils.getBucketName(pathString)

    method match {
      // aws multipart part upload eg. PUT /ObjectName?uploadId=UploadId
      case HttpMethods.PUT if containsUploadId => MultipartRequestType(uploadId(queryString), completeMultipartUpload = false)

      // aws multipart complete eg. POST /ObjectName?uploadId=UploadId and content-type application/xml
      case HttpMethods.POST if containsUploadId && (isXML || isOctetStream) => MultipartRequestType(uploadId(queryString), completeMultipartUpload = true)

      //for cacheRulesV1 - we cache only Get object and need to know when a modification object happens
      case HttpMethods.GET if s3path.isDefined && s3Object.isDefined => GetObjectRequestType()
      //for cacheRulesV1 - we need to know when a modification object happens to invalidate cache
      //TODO do it more specific (object/bucket/dir...)
      case HttpMethods.PUT => PutObjectRequestType()
      case HttpMethods.POST => PostObjectRequestType()
      case HttpMethods.DELETE => DeleteObjectRequestType()
      case HttpMethods.HEAD => HeadObjectRequestType()
      case _ => RequestTypeUnknown()
    }
  }

}
