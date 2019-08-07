package com.ing.wbaa.rokku.proxy.handler.parsers

import akka.http.scaladsl.model.{ HttpMethods, HttpRequest, MediaTypes }
import com.ing.wbaa.rokku.proxy.handler.parsers.RequestParser.{ AWSRequest, MultipartRequest, RequestUnknown }

object RequestParser {

  trait RecognizedRequest

  case class RequestUnknown() extends RecognizedRequest

  case class MultipartRequest(uploadId: String, completeMultipartUpload: Boolean) extends RecognizedRequest

  case class AWSRequest(recognizedRequest: RecognizedRequest)

}

trait RequestParser {

  private def uploadId(queryString: String) = queryString
    .split("&")
    .filter(_.contains("uploadId")).flatMap(_.split("=")).toList(1)

  def awsRequestFromRequest(request: HttpRequest): AWSRequest = {
    val HttpRequest(method, uri, headers, _, _) = request
    val queryString = uri.queryString().getOrElse("")
    val containsUploadId = queryString.contains("uploadId")
    val isXML = request.entity.contentType.mediaType == MediaTypes.`application/xml`
    val isOctetStream = request.entity.contentType.mediaType == MediaTypes.`application/octet-stream`

    method match {
      // aws multipart part upload eg. PUT /ObjectName?uploadId=UploadId
      case HttpMethods.PUT if containsUploadId => AWSRequest(MultipartRequest(uploadId(queryString), false))
      // aws multipart complete eg. POST /ObjectName?uploadId=UploadId and content-type application/xml
      case HttpMethods.POST if containsUploadId && (isXML || isOctetStream) => AWSRequest(MultipartRequest(uploadId(queryString), true))
      case _ => AWSRequest(RequestUnknown())
    }
  }

}
