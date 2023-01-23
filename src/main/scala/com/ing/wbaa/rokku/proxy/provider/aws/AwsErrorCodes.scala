package com.ing.wbaa.rokku.proxy.provider.aws

import akka.http.scaladsl.model.{ StatusCode, StatusCodes }
import com.ing.wbaa.rokku.proxy.data.RequestId

import scala.xml.NodeSeq

/**
 * https://docs.aws.amazon.com/AmazonS3/latest/API/ErrorResponses.html#ErrorCodeList
 */
object AwsErrorCodes {

  val errors: Map[StatusCode, (String, String)] =
    Map(
      StatusCodes.Forbidden -> (("AccessDenied", "Access Denied")),
      StatusCodes.InternalServerError -> (("InternalServerError", "Internal Server Error")),
      StatusCodes.Unauthorized -> (("Unauthorized", "Unauthorized")),
      StatusCodes.TooManyRequests -> (("TooManyRequests", "Too Many Requests")),
      StatusCodes.ServiceUnavailable -> (("Throttling", "SLOW DOWN")),
      StatusCodes.NotFound -> (("BucketNotFound", "Bucket not found in any namespace")),
      StatusCodes.MethodNotAllowed -> (("ListingAllBucketsNotAllowed", "Listing all buckets is disabled - please list only objects from bucket you have access to")))

  def response(code: StatusCode, resource: String = "")(implicit requestId: RequestId = RequestId("")): NodeSeq = {
    val responseError = errors.getOrElse(code, ("Unexpected Error", "Unexpected Error"))
    <Error>
      <Code>{ responseError._1 }</Code>
      <Message>{ responseError._2 }</Message>
      <Resource>{ resource }</Resource>
      <RequestId>{ requestId.value }</RequestId>
    </Error>
  }
}
