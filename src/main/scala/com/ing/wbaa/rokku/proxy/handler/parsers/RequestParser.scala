package com.ing.wbaa.rokku.proxy.handler.parsers

import akka.http.scaladsl.model.{ HttpMethods, HttpRequest, MediaTypes }
import com.ing.wbaa.rokku.proxy.handler.parsers.RequestParser._
import com.ing.wbaa.rokku.proxy.util.S3Utils

object RequestParser {

  sealed trait AWSRequestType
  sealed trait AWSMetaRequestType extends AWSRequestType

  case class RequestTypeUnknown() extends AWSRequestType

  case class MultipartRequestType(uploadId: String, completeMultipartUpload: Boolean) extends AWSRequestType

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

  case class MultiDeleteRequestType() extends AWSRequestType

  case class HeadObjectRequestType() extends AWSMetaRequestType

  case class HeadBucketRequestType() extends AWSMetaRequestType
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
    val s3Bucket = S3Utils.getBucketName(pathString)

    method match {
      // aws multipart part upload eg. PUT /ObjectName?uploadId=UploadId
      case HttpMethods.PUT if containsUploadId => MultipartRequestType(uploadId(queryString), completeMultipartUpload = false)

      // aws multipart complete eg. POST /ObjectName?uploadId=UploadId and content-type application/xml
      case HttpMethods.POST if containsUploadId && (isXML || isOctetStream) => MultipartRequestType(uploadId(queryString), completeMultipartUpload = true)

      //TODO verify create right rules
      // if queryString is empty multipart os are not covered
      case HttpMethods.GET if s3Object.isDefined && queryString.isEmpty => GetObjectRequestType()
      case HttpMethods.PUT if s3Object.isDefined && queryString.isEmpty => PutObjectRequestType()
      case HttpMethods.POST if s3Object.isDefined && queryString.isEmpty => PostObjectRequestType() // I think it is not needed if no queryString?
      case HttpMethods.DELETE if s3Object.isDefined && queryString.isEmpty => DeleteObjectRequestType()

      // object operations, put / delete etc.
      //case S3Request(_, Some(s3path), Some(_), _, _, _, _) => GetOrPutObjectRequestType()
      case HttpMethods.GET | HttpMethods.DELETE | HttpMethods.PUT if s3path.isDefined && s3Object.isDefined => ObjectOpsRequestType()

      // object operation as subfolder, in this case object can be empty
      // we need this to differentiate subfolder create/delete from bucket create/delete
      case HttpMethods.GET | HttpMethods.DELETE | HttpMethods.PUT if s3path.isDefined && !s3Object.isDefined && s3path.get.endsWith("/") => SubFolderOpsRequestType()

      // list-objects in the bucket operation
      case HttpMethods.GET if s3path.isDefined && !s3Object.isDefined => ListObjectsRequestType()

      // multidelete with xml list of objects in post
      case HttpMethods.POST if s3path.isDefined && !s3Object.isDefined && (isXML || isOctetStream) => MultiDeleteRequestType()

      // list / create / delete bucket operation
      case HttpMethods.GET | HttpMethods.PUT | HttpMethods.DELETE if !s3Bucket.isEmpty && !s3Object.isDefined => BucketOpsRequestType()

      // list buckets
      case HttpMethods.GET if !s3path.isDefined && !s3Object.isDefined => ListBucketsRequestType()

      // head bucket
      case HttpMethods.HEAD if s3path.isDefined && !s3Object.isDefined => HeadBucketRequestType()

      // head bucket
      case HttpMethods.HEAD if s3path.isDefined && s3Object.isDefined => HeadObjectRequestType()

      case _ => RequestTypeUnknown()
    }
  }

}
