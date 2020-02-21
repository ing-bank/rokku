package com.ing.wbaa.rokku.proxy.handler

import akka.http.scaladsl.model._
import com.ing.wbaa.rokku.proxy.handler.parsers.RequestParser
import com.ing.wbaa.rokku.proxy.handler.parsers.RequestParser.{ MultipartRequestType, RequestTypeUnknown }
import org.scalatest.diagrams.Diagrams
import org.scalatest.wordspec.AnyWordSpec

class RequestParserSpec extends AnyWordSpec with Diagrams with RequestParser {

  val uri = Uri("http://localhost:8987/demobucket/ObjectName?uploadId=1")
  val httpRequest: HttpMethod => HttpRequest = m => HttpRequest(m, uri)

  val partParseResult = awsRequestFromRequest(httpRequest(HttpMethods.PUT)).asInstanceOf[MultipartRequestType]
  val completeParseResult = awsRequestFromRequest(httpRequest(HttpMethods.POST)
    .withEntity(ContentType(MediaTypes.`application/xml`, HttpCharsets.`UTF-8`), "abc".getBytes())).asInstanceOf[MultipartRequestType]

  "RequestParser" should {
    "recognize multipart upload part request" in {
      assert(partParseResult.uploadId == "1" && !partParseResult.completeMultipartUpload)
    }
    "recognize multipart upload complete request" in {
      assert(completeParseResult.uploadId == "1" && completeParseResult.completeMultipartUpload)
    }
    "return RequestUnknown for other type of request" in {
      assert(awsRequestFromRequest(httpRequest(HttpMethods.HEAD)).isInstanceOf[RequestTypeUnknown])
    }
  }
}
