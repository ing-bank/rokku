package com.ing.wbaa.gargoyle.proxy.api.directive

import akka.http.scaladsl.model.{HttpHeader, HttpMethods}
import akka.http.scaladsl.server.Directive1
import com.ing.wbaa.gargoyle.proxy.data.{AccessType, S3Request}
import com.typesafe.scalalogging.LazyLogging

object ProxyDirectives extends LazyLogging {

  import akka.http.scaladsl.server.Directives._

  private case class AuthorizationHeaderS3(accessKey: String)

  /**
    * Extract data from the Authorization header of S3
    */
  private val extractAuthorizationS3: HttpHeader => Option[AuthorizationHeaderS3] = {
    case h if h.is("authorization") =>
      val credential =
        """\S+ Credential=(\S+), """.r
          .findFirstMatchIn(h.value())
          .map(_ group 1)

      credential.map(_.split("/")(0)).map(AuthorizationHeaderS3)
    case _ => None
  }

  val extracts3Request: Directive1[S3Request] =
    extractRequest tflatMap { case Tuple1(httpRequest) =>
      optionalHeaderValueByName("x-amz-security-token") tflatMap { case Tuple1(sessionToken) =>
        headerValue[AuthorizationHeaderS3](extractAuthorizationS3) tmap { case Tuple1(authHeaderS3) =>

          val bucket = {
            val firstPath = httpRequest.uri.path.tail.toString
            if (firstPath.isEmpty) None
            else Some(firstPath)
          }

          val accessType =
            if (httpRequest.method == HttpMethods.GET) AccessType.read
            else AccessType.write

          val s3Request = new S3Request(authHeaderS3.accessKey, sessionToken, bucket, accessType)
          logger.debug(s"Extracted S3 Request: $s3Request")
          s3Request
        }
      }
    }
}



