package com.ing.wbaa.gargoyle.proxy.api.directive

import akka.http.scaladsl.model.{ HttpHeader, HttpMethods }
import akka.http.scaladsl.server.Directive1
import com.ing.wbaa.gargoyle.proxy.data._
import com.typesafe.scalalogging.LazyLogging

object ProxyDirectives extends LazyLogging {

  import akka.http.scaladsl.server.Directives._

  private case class AuthorizationHeaderS3(accessKey: String)

  /**
   * Extract data from the Authorization header of S3
   */
  private val extractAuthorizationS3: HttpHeader => Option[AuthorizationHeaderS3] = {
    case h if h.is("authorization") =>
      val signerType = h.value().split(" ").headOption
      logger.debug(s"Signertype used: $signerType")

      signerType match {
        case Some("AWS4-HMAC-SHA256") =>
          val credential =
            """\S+ Credential=(\S+), """.r
              .findFirstMatchIn(h.value())
              .map(_ group 1)

          credential.flatMap(_.split("/").headOption).map(AuthorizationHeaderS3)

        case Some("AWS") =>
          val accessKey =
            """AWS (\S+):\S+""".r
              .findFirstMatchIn(h.value())
              .map(_ group 1)

          accessKey.map(AuthorizationHeaderS3)

        case _ =>
          logger.warn(s"The necessary information couldn't be extracted from the authorization header, " +
            s"this could be caused by a signer type that we don't support yet...: $h")
          None
      }

    case _ => None
  }

  val extracts3Request: Directive1[S3Request] =
    extractRequest tflatMap { case Tuple1(httpRequest) =>
      optionalHeaderValueByName("x-amz-security-token") tflatMap { case Tuple1(sessionToken) =>
        headerValue[AuthorizationHeaderS3](extractAuthorizationS3) tmap { case Tuple1(authHeaderS3) =>

          val bucket = httpRequest.uri.path.toString
            .split("/")
            .toList
            .lift(1)
            .flatMap(b => if (b.isEmpty) None else Some(b))

          val accessType = if (httpRequest.method == HttpMethods.GET) Read else Write

          val s3Request = S3Request(
            AwsRequestCredential(AwsAccessKey(authHeaderS3.accessKey), sessionToken.map(AwsSessionToken)),
            bucket,
            accessType
          )

          logger.debug(s"Extracted S3 Request: $s3Request")
          s3Request
        }
      }
    }
}

