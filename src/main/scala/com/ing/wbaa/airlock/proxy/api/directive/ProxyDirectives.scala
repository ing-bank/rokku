package com.ing.wbaa.airlock.proxy.api.directive

import java.net.InetAddress

import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.{ Directive0, Directive1 }
import com.ing.wbaa.airlock.proxy.data._
import com.ing.wbaa.airlock.proxy.metrics.MetricsFactory
import com.typesafe.scalalogging.LazyLogging

import scala.language.postfixOps
import scala.util.Try

object ProxyDirectives extends LazyLogging {

  import akka.http.scaladsl.server.Directives._

  private[this] val AUTHORIZATION_HTTP_HEADER_NAME = "authorization"
  private[this] val X_FORWARDED_FOR_HEADER = "X-Forwarded-For"
  private[this] val X_FORWARDED_PROTO_HEADER = "X-Forwarded-Proto"
  private[this] val X_REAL_IP_HEADER = "X-Real-IP"
  private[this] val REMOTE_ADDRESS_HEADER = "Remote-Address"

  private[this] val ipv4Regex = "\\s*([0-9\\.]+)(:([0-9]+))?\\s*" r

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

  val extracts3Request: Directive1[S3Request] =
    extractClientIP tflatMap { case Tuple1(clientIPAddress) =>
      logger.debug(s"Extracted Client IP: " +
        s"${clientIPAddress.toOption.map(_.getHostAddress).getOrElse("unknown")}")
      extractHeaderIPs tflatMap { case Tuple1(headerIPs) =>
        logger.debug(s"Extracted headers IPs: $headerIPs")
        extractRequest tflatMap { case Tuple1(httpRequest) =>
          optionalHeaderValueByName("x-amz-security-token") tflatMap {
            case Tuple1(optionalSessionToken) =>
              headerValue[AwsAccessKey](extractAuthorizationS3) tmap { case Tuple1(awsAccessKey) =>

                val rootPath =
                  if (httpRequest.uri.path.endsWithSlash) httpRequest.uri.path.toString().dropRight(1)
                  else httpRequest.uri.path.toString()

                // aws is passing subdir in prefix parameter if no object is used, eg. list bucket objects
                val s3path = httpRequest.uri.rawQueryString match {
                  case Some(queryString) if queryString.contains("prefix") =>
                    val queryPrefixPair = queryString
                      .split("&")
                      .filter(_.contains("prefix"))
                      .head.split("=")

                    val delimiter = queryString
                      .split("&").find(_.contains("delimiter")) match {
                        case Some(d)         => d.split("=").last
                        case None            => "/"
                      }

                    if (queryPrefixPair.length == 2) {
                      Uri.Path(s"$rootPath/${queryPrefixPair.last.replace(delimiter, "/")}")
                    } else {
                      Uri.Path(s"$rootPath")
                    }
                  case _         => Uri.Path(s"$rootPath")
                }

                val s3Request = S3Request(
                  AwsRequestCredential(awsAccessKey, optionalSessionToken.map(AwsSessionToken)),
                  s3path,
                  httpRequest.method,
                  clientIPAddress,
                  headerIPs
                )

                logger.debug(s"Extracted S3 Request: $s3Request")
                s3Request
              }
          }
        }
      }
    }

  /**
   * Updates the forward headers for a request.
   * Since we're proxy requests through Airlock to S3, we need to add the remote address to the forward headers.
   */
  val updateHeadersForRequest: Directive1[HttpRequest] =
    extractRequest tflatMap { case Tuple1(httpRequest) =>
      optionalHeaderValueByName(REMOTE_ADDRESS_HEADER) tflatMap { case Tuple1(remoteAddressHeader) =>
        optionalHeaderValueByName(X_FORWARDED_FOR_HEADER) tmap { case Tuple1(xForwardedForHeader) =>

          val prependForwardedFor = xForwardedForHeader match {
            case Some(forwardHeader)       => s"$forwardHeader, "
            case None                      => ""
          }

          val newHeaders: Seq[HttpHeader] =
            httpRequest.headers
              .filter(h =>
                h.isNot(X_FORWARDED_FOR_HEADER.toLowerCase) && h.isNot(X_FORWARDED_PROTO_HEADER.toLowerCase)
              ) ++ List(
                RawHeader(X_FORWARDED_FOR_HEADER, prependForwardedFor + remoteAddressHeader.map(_.split(":").head).getOrElse("unknown")),
                RawHeader(X_FORWARDED_PROTO_HEADER, httpRequest._5.value)
              )

          httpRequest.withHeaders(newHeaders.toList)
        }
      }
    }

  /**
   * Extract the list of IPs in the X-Forwarded-For, X-Real-Ip and Remote-Address headers.
   */
  val extractHeaderIPs: Directive1[HeaderIPs] =
    optionalRawHeaderValueByName(X_REAL_IP_HEADER) &
      optionalRawHeaderValueByName(X_FORWARDED_FOR_HEADER) &
      optionalRawHeaderValueByName(REMOTE_ADDRESS_HEADER) tflatMap { case (xRealIP, xForwardedFor, remoteAddress) =>
        provide(HeaderIPs(
          `X-Real-IP` = xRealIP.map(extractIP),
          `X-Forwarded-For` = xForwardedFor.map(_.split(',').map(extractIP)),
          `Remote-Address` = remoteAddress.map(extractIP)
        ))
      }

  /*
   *  This directive is required to correctly intercept the user provided headers.
   *  The behavior is the same as optionalHeaderValueByName, but it makes sure that
   *  the header is instance of RawHeader. This check is necessary because Akka is
   *  injecting headers (e.g. Remote-Address), which may be interpreted as user headers.
   */
  private def optionalRawHeaderValueByName(name: String): Directive1[Option[String]] =
    optionalHeaderValuePF({
      case h: RawHeader if h.lowercaseName == name.toLowerCase => h.value
    })

  private def extractIP(address: String): RemoteAddress =
    Try {
      address match {
        case ipv4Regex(ip, _, port) =>
          RemoteAddress(InetAddress.getByName(ip), Option(port).map(_.toInt))
        case _ =>
          logger.warn(s"Unable to parse IP address ${address}")
          RemoteAddress.Unknown
      }
    } getOrElse RemoteAddress.Unknown

  val metricDuration: Directive0 = extractRequestContext.flatMap { _ =>
    val start = System.nanoTime()
    mapResponse { response =>
      val took = System.nanoTime() - start
      MetricsFactory.markRequestTime(took)
      response.status match {
        case StatusCodes.InternalServerError => MetricsFactory.countRequest(MetricsFactory.FAILURE_REQUEST)
        case StatusCodes.Forbidden           => MetricsFactory.countRequest(MetricsFactory.UNAUTHENTICATED_REQUEST)
        case _                               => MetricsFactory.countRequest(MetricsFactory.SUCCESS_REQUEST)
      }
      response
    }
  }
}

