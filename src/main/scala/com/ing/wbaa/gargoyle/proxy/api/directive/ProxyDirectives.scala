package com.ing.wbaa.gargoyle.proxy.api.directive

import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.server.{ Directive1, MissingHeaderRejection }
import com.ing.wbaa.gargoyle.proxy.data._
import com.typesafe.scalalogging.LazyLogging

object ProxyDirectives extends LazyLogging {

  import akka.http.scaladsl.server.Directives._

  /**
   * Extract data from the Authorization header of S3
   */
  private[this] def extractAuthorizationS3(httpHeader: HttpHeader): Option[AwsAccessKey] =
    if (httpHeader.is("authorization")) {
      val signerType = httpHeader.value().split(" ").headOption
      logger.debug(s"Signertype used: $signerType")

      signerType match {
        case Some("AWS4-HMAC-SHA256") =>
          val credential =
            """\S+ Credential=(\S+), """.r
              .findFirstMatchIn(httpHeader.value())
              .map(_ group 1)

          credential.flatMap(_.split("/").headOption).map(AwsAccessKey)

        case Some("AWS") =>
          val accessKey =
            """AWS (\S+):\S+""".r
              .findFirstMatchIn(httpHeader.value())
              .map(_ group 1)

          accessKey.map(AwsAccessKey)

        case _ =>
          logger.warn(s"The necessary information couldn't be extracted from the authorization header, " +
            s"this could be caused by a signer type that we don't support yet...: $httpHeader")
          None
      }
    } else None

  val extracts3Request: Directive1[S3Request] =
    extractRequest tflatMap { case Tuple1(httpRequest) =>
      optionalHeaderValueByName("x-amz-security-token") tflatMap {
        case Tuple1(Some(sessionToken)) =>
          headerValue[AwsAccessKey](extractAuthorizationS3) tmap { case Tuple1(awsAccessKey) =>

            val s3Request = S3Request(
              AwsRequestCredential(awsAccessKey, AwsSessionToken(sessionToken)),
              httpRequest.uri.path,
              httpRequest.method
            )

            logger.debug(s"Extracted S3 Request: $s3Request")
            s3Request
          }
        case Tuple1(None) =>
          logger.info("STS token not provided in header of request")
          reject(MissingHeaderRejection("x-amz-security-token"))
      }
    }
}

