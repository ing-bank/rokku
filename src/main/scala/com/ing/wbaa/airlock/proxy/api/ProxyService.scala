package com.ing.wbaa.airlock.proxy.api

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse, RemoteAddress, StatusCodes }
import akka.http.scaladsl.server.{ AuthorizationFailedRejection, Route }
import com.ing.wbaa.airlock.proxy.api.directive.ProxyDirectives
import com.ing.wbaa.airlock.proxy.config.AtlasSettings
import com.ing.wbaa.airlock.proxy.data._
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

trait ProxyService extends LazyLogging {

  // no validation of request currently
  // once we get comfortable with get/put/del we can add permCheck
  import ProxyDirectives._
  import akka.http.scaladsl.server.Directives._

  protected[this] implicit def system: ActorSystem

  protected[this] implicit def executionContext: ExecutionContext

  // Request Handler methods
  protected[this] def executeRequest(request: HttpRequest, userSTS: User): Future[HttpResponse]

  // Authentication methods
  protected[this] def areCredentialsActive(awsRequestCredential: AwsRequestCredential): Future[Option[User]]

  // AWS Signature methods
  protected[this] def isUserAuthenticated(httpRequest: HttpRequest, awsSecretKey: AwsSecretKey): Boolean

  // Authorization methods
  protected[this] def isUserAuthorizedForRequest(request: S3Request, user: User, clientIPAddress: RemoteAddress, forwardedForAddresses: Seq[RemoteAddress]): Boolean

  // Atlas Lineage
  protected[this] def atlasSettings: AtlasSettings

  protected[this] def createLineageFromRequest(httpRequest: HttpRequest, userSTS: User, clientIPAddress: RemoteAddress): Future[LineageResponse]

  val proxyServiceRoute: Route =
    withoutSizeLimit {
      (extractClientIP & extractForwardedForIPs) { (clientIPAddress, forwardedForAddresses) =>
        logger.debug(s"Extracted Client IP: " +
          s"${clientIPAddress.toOption.map(_.getHostAddress).getOrElse("unknown")}")
        logger.debug(s"Extracted Forwarded-For IPs: " +
          s"${forwardedForAddresses.map(_.toOption.map(_.getHostAddress).getOrElse("unknown"))}")
        extractRequest { httpRequest =>
          extracts3Request { s3Request =>
            onComplete(areCredentialsActive(s3Request.credential)) {
              case Success(Some(userSTS: User)) =>
                logger.debug(s"Credentials active for request, user retrieved: $userSTS")
                processRequestForValidUser(clientIPAddress, forwardedForAddresses, httpRequest, s3Request, userSTS)
              case Success(None) =>
                val msg = s"Request not authenticated: $s3Request"
                logger.warn(msg)
                complete((StatusCodes.Forbidden, msg))
              case Failure(exception) =>
                logger.error(s"An error occurred checking authentication with STS service", exception)
                complete(StatusCodes.InternalServerError)
            }
          }
        }
      }
    }

  protected[this] def processAuthorizedRequest(httpRequest: HttpRequest, s3Request: S3Request, userSTS: User, clientIPAddress: RemoteAddress): Route = {
    updateHeadersForRequest { newHttpRequest =>
      val httpResponse = executeRequest(newHttpRequest, userSTS).andThen {
        case Success(response: HttpResponse) =>
          if (atlasSettings.atlasEnabled && (response.status == StatusCodes.OK || response.status == StatusCodes.NoContent))
            // delete on AWS response 204
            createLineageFromRequest(httpRequest, userSTS, clientIPAddress)
      }
      complete(httpResponse)
    }
  }

  private def processRequestForValidUser(clientIPAddress: RemoteAddress, forwardedForAddresses: Seq[RemoteAddress], httpRequest: HttpRequest, s3Request: S3Request, userSTS: User) = {
    if (isUserAuthenticated(httpRequest, userSTS.secretKey)) {
      logger.debug(s"Request authenticated: $httpRequest")

      if (isUserAuthorizedForRequest(s3Request, userSTS, clientIPAddress, forwardedForAddresses)) {
        logger.info(s"User (${userSTS.userName}) successfully authorized for request: $s3Request")

        processAuthorizedRequest(httpRequest, s3Request, userSTS, clientIPAddress)

      } else {
        logger.warn(s"User (${userSTS.userName}) not authorized for request: $s3Request")
        reject(AuthorizationFailedRejection)
      }
    } else {
      logger.warn(s"Request not authenticated: $httpRequest")
      complete(StatusCodes.Forbidden)
    }
  }
}
