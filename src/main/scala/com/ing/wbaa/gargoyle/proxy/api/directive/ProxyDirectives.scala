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
      // TODO: I added a new capture group below, this needs to be extracted as the signer type
      val credential =
        """(\S+) Credential=(\S+), """.r
          .findFirstMatchIn(h.value())
          .map(_ group 1)

      // TODO: add different extraction method of accesskey for various signer types
      // First we support now is: AWS4-HMAC-SHA256
      // Well need to support: authorization: AWS accesskey:RFQyL2CuCfZKCfPpXXA1R129GXo=
      logger.debug(h.toString())
      logger.debug("Extracted: ", credential.map(_.split("/")))
      val ret = credential.flatMap(_.split("/").headOption).map(AuthorizationHeaderS3)
      if (ret.isEmpty) logger.warn(s"The necessary information couldn't be extracted from the authorization header, " +
        s"this could be caused by a signer type that we don't support yet...: $h")
      ret
    case _ => None
  }

  val extracts3Request: Directive1[S3Request] =
    extractRequest tflatMap { case Tuple1(httpRequest) =>
      optionalHeaderValueByName("x-amz-security-token") tflatMap { case Tuple1(sessionToken) =>
        headerValue[AuthorizationHeaderS3](extractAuthorizationS3) tmap { case Tuple1(authHeaderS3) =>

          val bucket = httpRequest.uri.path.toString.split("/").toList.lift(1).flatMap(b => if (b.isEmpty) None else Some(b))

          val accessType =
            if (httpRequest.method == HttpMethods.GET) AccessType.read
            else AccessType.write

          val s3Request = S3Request(authHeaderS3.accessKey, sessionToken, bucket, accessType)
          logger.debug(s"Extracted S3 Request: $s3Request")
          s3Request
        }
      }
    }
}



