package com.ing.wbaa.rokku.proxy.handler.parsers

import akka.http.scaladsl.model.{HttpMethods, HttpRequest, MediaTypes}
import com.ing.wbaa.rokku.proxy.handler.parsers.RequestParser.{AWSRequestType, GetObject, MultipartRequestType, PostObject, PutObject, RequestTypeUnknown}

object RequestParser {

  sealed trait AWSRequestType

  case class RequestTypeUnknown() extends AWSRequestType

  case class MultipartRequestType(uploadId: String, completeMultipartUpload: Boolean) extends AWSRequestType

  class CreateObject() extends AWSRequestType

  case class GetObject() extends AWSRequestType

  case class PutObject() extends CreateObject

  case class PostObject() extends CreateObject

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

    method match {
      // aws multipart part upload eg. PUT /ObjectName?uploadId=UploadId
      case HttpMethods.PUT if containsUploadId => MultipartRequestType(uploadId(queryString), completeMultipartUpload = false)
      // aws multipart complete eg. POST /ObjectName?uploadId=UploadId and content-type application/xml
      case HttpMethods.POST if containsUploadId && (isXML || isOctetStream) => MultipartRequestType(uploadId(queryString), completeMultipartUpload = true)
      case HttpMethods.GET if queryString.isEmpty => GetObject()
      case HttpMethods.PUT if queryString.isEmpty => PutObject()
      case HttpMethods.POST if queryString.isEmpty => PostObject()
      case _ => RequestTypeUnknown()
    }
  }

}
