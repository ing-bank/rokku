package com.ing.wbaa.airlock.proxy.api.directive

import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{ HttpHeader, HttpRequest }
import akka.http.scaladsl.server.Directive1
import com.ing.wbaa.airlock.proxy.data._
import com.typesafe.scalalogging.LazyLogging

object ProxyDirectives extends LazyLogging {

  import akka.http.scaladsl.server.Directives._

  private[this] val AUTHORIZATION_HTTP_HEADER_NAME = "authorization"

  /**
   * Extract data from the Authorization header of S3
   */
  private[this] def extractAuthorizationS3(httpHeader: HttpHeader): Option[AwsAccessKey] =
    if (httpHeader.is(AUTHORIZATION_HTTP_HEADER_NAME)) {
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

  //TODO: Put Remote IP Address in the S3 Request, call it IP Origin
  val extracts3Request: Directive1[S3Request] =
    extractRequest tflatMap { case Tuple1(httpRequest) =>
      optionalHeaderValueByName("x-amz-security-token") tflatMap {
        case Tuple1(optionalSessionToken) =>
          headerValue[AwsAccessKey](extractAuthorizationS3) tmap { case Tuple1(awsAccessKey) =>

            val s3Request = S3Request(
              AwsRequestCredential(awsAccessKey, optionalSessionToken.map(AwsSessionToken)),
              httpRequest.uri.path,
              httpRequest.method
            )

            logger.debug(s"Extracted S3 Request: $s3Request")
            s3Request
          }
      }
    }

  /**
   * Updates the forward headers of a request.
   */
  val updateHeadersForRequest: Directive1[HttpRequest] =
    extractRequest tflatMap { case Tuple1(httpRequest) =>
      optionalHeaderValueByName("Remote-Address") tflatMap { case Tuple1(remoteAddressHeader) =>
        optionalHeaderValueByName("X-Forwarded-For") tmap { case Tuple1(xForwardedForHeader) =>
          val prependForwardedFor = xForwardedForHeader match {
            case Some(ffh)       => s"$ffh, "
            case None            => ""
          }

          val newHeaders: Seq[HttpHeader] =
            httpRequest.headers
              .filter(h =>
                h.isNot("x-forwarded-for") && h.isNot("x-forwarded-proto")
              ) ++ List(
                RawHeader("X-Forwarded-For", prependForwardedFor + remoteAddressHeader.map(_.split(":").head).getOrElse("unknown")),
                RawHeader("X-Forwarded-Proto", httpRequest._5.value)
              )

          httpRequest.withHeaders(newHeaders.toList)
        }
      }
    }
}

