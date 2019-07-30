package com.ing.wbaa.rokku.proxy.provider.aws

import akka.http.scaladsl.model.{ StatusCode, StatusCodes }

import scala.xml.NodeSeq

/**
 * https://docs.aws.amazon.com/AmazonS3/latest/API/ErrorResponses.html#ErrorCodeList
 */
object AwsErrorCodes {

  val errors: Map[StatusCode, (String, String)] =
    Map(
      StatusCodes.Forbidden -> (("AccessDenied", "Access Denied")),
      StatusCodes.InternalServerError -> (("ServiceUnavailable", "Reduce your request rate.")),
      StatusCodes.Unauthorized -> (("Unauthorized", "Unauthorized")))

  def response(code: StatusCode, resource: String = "", requestId: String = ""): NodeSeq = {
    val responseError = errors.getOrElse(code, ("Unexpected Error", "Unexpected Error"))
    <Error>
      <Code>{ responseError._1 }</Code>
      <Message>{ responseError._2 }</Message>
      <Resource>{ resource }</Resource>
      <RequestId>{ requestId }</RequestId>
    </Error>
  }
}
