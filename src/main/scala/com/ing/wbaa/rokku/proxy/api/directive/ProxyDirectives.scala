package com.ing.wbaa.rokku.proxy.api.directive

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{ RawHeader, `Access-Control-Allow-Origin` }
import akka.http.scaladsl.server.{ Directive, Directive0, Directive1 }
import com.ing.wbaa.rokku.proxy.data._
import com.ing.wbaa.rokku.proxy.metrics.MetricsFactory
import com.ing.wbaa.rokku.proxy.provider.aws.SignatureHelpersCommon._
import com.ing.wbaa.rokku.proxy.util.S3Utils
import com.typesafe.scalalogging.LazyLogging

import java.net.InetAddress
import scala.language.postfixOps
import scala.util.Try

object ProxyDirectives extends LazyLogging {

  import akka.http.scaladsl.server.Directives._

  private[this] val AUTHORIZATION_HTTP_HEADER_NAME = "authorization"
  private[this] val X_FORWARDED_FOR_HEADER = "X-Forwarded-For"
  private[this] val X_FORWARDED_PROTO_HEADER = "X-Forwarded-Proto"
  private[this] val X_REAL_IP_HEADER = "X-Real-IP"
  private[this] val REMOTE_ADDRESS_HEADER = "Remote-Address"
  private[this] val ORIGIN = "Origin"
  private[this] val X_AMZ_CONTENT_SHA256 = "X-Amz-Content-SHA256"
  private[this] val RAW_REQUEST_URI = "Raw-Request-URI"

  private[this] val ipv4Regex = "\\s*([0-9\\.]+)(:([0-9]+))?\\s*" r

  /**
   * Extract data from the Authorization header of S3
   */
  private[this] def extractAuthorizationS3(httpHeader: HttpHeader): Option[AwsAccessKey] =
    if (httpHeader.is(AUTHORIZATION_HTTP_HEADER_NAME)) {
      val signerType = httpHeader.value().split(" ").headOption
      logger.debug("Signertype used: {}", signerType)
      getAccessKeyFromAuthString(httpHeader.value(), signerType)
    } else None

  private[this] def getAccessKeyFromAuthString(authString: String, signerType: Option[String]): Option[AwsAccessKey] = {
    signerType match {
      case Some("AWS4-HMAC-SHA256") =>
        val credential =
          """\S+ Credential=(\S+), """.r
            .findFirstMatchIn(authString)
            .map(_ group 1)

        credential.flatMap(_.split("/").headOption).map(AwsAccessKey)

      case Some("AWS") =>
        val accessKey =
          """AWS (\S+):\S+""".r
            .findFirstMatchIn(authString)
            .map(_ group 1)

        accessKey.map(AwsAccessKey)

      case _ =>
        logger.warn("The necessary information couldn't be extracted from the authorization header, " +
          s"this could be caused by a signer type that we don't support yet...: {}", authString)
        None
    }
  }

  private[this] def extractAuthorizationS3(signerType: String, authString: String): Option[AwsAccessKey] = {
    //to use the method getAccessKeyFromAuthString we need to add something at the beginning and colon after - maybe we need think about sth else (to have one method the extract auth)
    val authStringWithPrefixAnsPostfix = "x Credential=" + authString + ", x"
    getAccessKeyFromAuthString(authStringWithPrefixAnsPostfix, Some(signerType))
  }

  private[this] def extractAuthorizationS3: Directive1[AwsAccessKey] = {
    headerValue[AwsAccessKey](extractAuthorizationS3) |
      parameter(X_AMZ_ALGORITHM.optional, X_AMZ_CREDENTIAL.optional).tflatMap {
        case (Some(algorithm), Some(credential)) =>
          extractAuthorizationS3(algorithm, credential).map(provide).getOrElse(reject)
        case _ => reject
      }
  }

  val extracts3Request: Directive1[S3Request] =
    extractClientIP tflatMap { case Tuple1(clientIPAddress) =>
      logger.debug(s"Extracted Client IP: {}", clientIPAddress.toOption.map(_.getHostAddress).getOrElse("unknown"))
      extractHeaderIPs tflatMap { case Tuple1(headerIPs) =>
        logger.debug(s"Extracted headers IPs : {}", headerIPs)
        extractPresignParams tflatMap { case Tuple1(presignParams) =>
          logger.debug(s"Extracted presign params : {}", presignParams)
          extractRequest tflatMap { case Tuple1(httpRequest) =>
            getAWSSessionToken tflatMap {
              case Tuple1(optionalSessionToken) =>
                extractAuthorizationS3 tmap { case Tuple1(awsAccessKey) =>
                  // aws is passing subdir in prefix parameter if no object is used, eg. list bucket objects
                  val s3path = S3Utils.getS3FullPathWithBucketName(httpRequest)
                  val s3Request = S3Request(
                    AwsRequestCredential(awsAccessKey, optionalSessionToken),
                    s3path,
                    httpRequest.method,
                    clientIPAddress,
                    headerIPs,
                    httpRequest.entity.contentType.mediaType,
                    presignParams
                  )

                  logger.debug(s"Extracted S3 Request: {}", s3Request)
                  s3Request
                }
            }
          }
        }
      }
    }

  /**
   * Updates the forward headers for a request.
   * Since we're proxy requests through Rokku to S3, we need to add the remote address to the forward headers.
   * If the request is presign we add authorization header to work with executeRequest method
   */
  def updateHeadersForRequest(httpRequest: HttpRequest): Directive1[HttpRequest] =
    optionalHeaderValueByName(REMOTE_ADDRESS_HEADER) tflatMap { case Tuple1(remoteAddressHeader) =>
      optionalHeaderValueByName(X_FORWARDED_FOR_HEADER) tflatMap { case Tuple1(xForwardedForHeader) =>
        extractPresignParams tmap { case Tuple1(presignParams) =>
          val prependForwardedFor = xForwardedForHeader match {
            case Some(forwardHeader)       => s"$forwardHeader, "
            case None                      => ""
          }

          var newHeaders: Seq[HttpHeader] =
            httpRequest.headers
              .filter(h =>
                h.isNot(X_FORWARDED_FOR_HEADER.toLowerCase) && h.isNot(X_FORWARDED_PROTO_HEADER.toLowerCase)
              ) ++ List(
                RawHeader(X_FORWARDED_FOR_HEADER, prependForwardedFor + remoteAddressHeader.map(_.split(":").head).getOrElse("unknown")),
                RawHeader(X_FORWARDED_PROTO_HEADER, httpRequest._5.value)
              )

          var uri = httpRequest.uri

          if (presignParams.isDefined) {
            val authStr = s"${presignParams.get(X_AMZ_ALGORITHM)} Credential=${presignParams.get(X_AMZ_CREDENTIAL)}, SignedHeaders=${presignParams.get(X_AMZ_SIGNED_HEADERS)}, Signature=${presignParams.get(X_AMZ_SIGNATURE)}"
            uri = httpRequest.uri.withQuery(Uri.Query.Empty)
            newHeaders = newHeaders.filter(_.isNot(RAW_REQUEST_URI.toLowerCase)) ++ List(
              RawHeader(AUTHORIZATION_HTTP_HEADER_NAME, authStr),
              RawHeader(X_AMZ_DATE, presignParams.get(X_AMZ_DATE)),
              RawHeader(X_AMZ_CONTENT_SHA256, "UNSIGNED-PAYLOAD")
            )
          }
          httpRequest.withHeaders(newHeaders.toList).withUri(uri)
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
          `X-Forwarded-For` = xForwardedFor.map(_.split(',').toIndexedSeq.map(extractIP)),
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
          logger.warn("Unable to parse IP address {}", address)
          RemoteAddress.Unknown
      }
    } getOrElse RemoteAddress.Unknown

  val metricDuration: Directive0 = extractRequestContext.flatMap {
    requestCtx =>
      val start = System.nanoTime()
      val requestMethodName = requestCtx.request.method.value.toLowerCase
      val requestContentLength = requestCtx.request.entity.contentLengthOption.getOrElse(0L)
      metricsContentLengthCount(requestMethodName, requestContentLength, "out")
      mapResponse { response =>
        val took = System.nanoTime() - start
        MetricsFactory.markRequestTime(took)
        response.status match {
          case StatusCodes.InternalServerError => MetricsFactory.countRequest(MetricsFactory.FAILURE_REQUEST)
          case StatusCodes.Forbidden           => MetricsFactory.countRequest(MetricsFactory.UNAUTHENTICATED_REQUEST)
          case _                               => MetricsFactory.countRequest(MetricsFactory.SUCCESS_REQUEST)
        }
        val responseContentLength = response.entity.contentLengthOption.getOrElse(0L)
        metricsContentLengthCount(requestMethodName, responseContentLength, "in")
        response
      }
  }

  private def metricsContentLengthCount(requestMethodName: String, contentLength: Long, inOrOutName: String): Unit = {
    if (contentLength > 0 && !requestMethodName.equals("head")) {
      MetricsFactory
        .countRequest(MetricsFactory.REQUEST_CONTEXT_LENGTH
          .replace(MetricsFactory.HTTP_METHOD, requestMethodName)
          .replace(MetricsFactory.HTTP_DIRECTION, inOrOutName), countAll = false)
      MetricsFactory
        .countRequest(MetricsFactory.REQUEST_CONTEXT_LENGTH_SUM
          .replace(MetricsFactory.HTTP_METHOD, requestMethodName)
          .replace(MetricsFactory.HTTP_DIRECTION, inOrOutName), contentLength, countAll = false)
    }
  }

  def cors(): Directive0 = {
    extractRequest.flatMap { request =>
      if (request.headers.exists(_.name().equals(ORIGIN))) {
        mapResponseHeaders { oldHeaders =>
          Seq(
            `Access-Control-Allow-Origin`.*,
            RawHeader("Access-Control-Allow-Methods", "*"),
            RawHeader("Access-Control-Expose-Headers", "ETag"),
            RawHeader("Access-Control-Allow-Headers", "*")) ++ oldHeaders
        }
      } else {
        pass
      }
    }
  }

  private def extractPresignParams: Directive[Tuple1[Option[Map[String, String]]]] = {
    parameters(
      X_AMZ_CREDENTIAL.optional,
      X_AMZ_ALGORITHM.optional,
      X_AMZ_SIGNED_HEADERS.optional,
      X_AMZ_SIGNATURE.optional,
      X_AMZ_EXPIRES.optional,
      X_AMZ_DATE.optional,
      X_AMZ_SECURITY_TOKEN.optional) tflatMap {
        case (Some(credential), Some(algorithm), Some(sighnedHeaders), Some(signature), Some(expires), Some(date), Some(token)) =>
          provide(
            Some(Map(
              X_AMZ_CREDENTIAL -> credential,
              X_AMZ_ALGORITHM -> algorithm,
              X_AMZ_SIGNED_HEADERS -> sighnedHeaders,
              X_AMZ_SIGNATURE -> signature,
              X_AMZ_EXPIRES -> expires,
              X_AMZ_DATE -> date,
              X_AMZ_SECURITY_TOKEN -> token
            )
            ))
        case _ =>
          provide(None)
      }
  }

  private def getAWSSessionToken: Directive[Tuple1[Option[AwsSessionToken]]] =
    optionalHeaderValueByName(X_AMZ_SECURITY_TOKEN) & parameter(X_AMZ_SECURITY_TOKEN.optional) tmap {
      case (optionalSessionToken, optionalSessionTokenFromParam) => optionalSessionToken.map(AwsSessionToken).orElse(optionalSessionTokenFromParam.map(AwsSessionToken))
    }
}

