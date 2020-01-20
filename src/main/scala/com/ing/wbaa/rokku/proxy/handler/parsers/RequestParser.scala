package com.ing.wbaa.rokku.proxy.handler.parsers

import akka.http.scaladsl.model.{ HttpMethods, HttpRequest, MediaTypes }
import com.ing.wbaa.rokku.proxy.handler.parsers.RequestParser.{ AWSRequestType, MultipartRequestType, ReadRequestType, RequestTypeUnknown }

object RequestParser {

  trait AWSRequestType

  case class RequestTypeUnknown() extends AWSRequestType

  case class MultipartRequestType(uploadId: String, completeMultipartUpload: Boolean) extends AWSRequestType

  case class ReadRequestType() extends AWSRequestType

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
      // read s3 object requests
      case HttpMethods.GET => ReadRequestType()

      case _ => RequestTypeUnknown()
    }
  }

}
