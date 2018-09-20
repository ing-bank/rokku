package com.ing.wbaa.gargoyle.proxy.api

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri.Authority
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Route
import com.ing.wbaa.gargoyle.proxy.api.directive.ProxyDirectives
import com.ing.wbaa.gargoyle.proxy.data.{ AwsRequestCredential, S3Request, User }
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Future
import scala.util.{ Failure, Success }

trait ProxyService extends LazyLogging {

  // no validation of request currently
  // once we get comfortable with get/put/del we can add permCheck
  import ProxyDirectives._
  import akka.http.scaladsl.server.Directives._

  protected[this] implicit def system: ActorSystem

  // Request Handler methods
  protected[this] def executeRequest(request: HttpRequest, clientAddress: RemoteAddress, userSTS: User): Future[HttpResponse]

  // Authentication methods
  protected[this] def getUserForAccessKey(awsRequestCredential: AwsRequestCredential): Future[Option[User]]
  protected[this] def areCredentialsAuthentic(awsRequestCredential: AwsRequestCredential): Future[Boolean]

  // Authorization methods
  protected[this] def isUserAuthorizedForRequest(request: S3Request, user: User): Boolean

  // Atlas method
  protected[this] def createLineageFromRequest(s3Request: S3Request, authority: Authority, contentType: ContentType): Future[Option[(String, String, String, String)]]

  val proxyServiceRoute: Route =
    withoutSizeLimit {
      extractClientIP { remoteAddress =>
        extractRequest { httpRequest =>
          extracts3Request { s3Request =>
            onComplete(areCredentialsAuthentic(s3Request.credential)) {
              case Success(true) =>
                logger.debug(s"Request authenticated: $s3Request")

                onComplete(getUserForAccessKey(s3Request.credential)) {
                  case Success(Some(userSTS: User)) =>
                    logger.debug(s"User retrieved: $userSTS")

                    if (isUserAuthorizedForRequest(s3Request, userSTS)) {
                      logger.info(s"User (${userSTS.userName}) successfully authorized for request: $s3Request")
                      createLineageFromRequest(s3Request, httpRequest.uri.authority, httpRequest.entity.contentType) //todo: make this resistent for atlas not reachable
                      complete(executeRequest(httpRequest, remoteAddress, userSTS))
                    } else {
                      val msg = s"User (${userSTS.userName}) not authorized for request: $s3Request"
                      logger.warn(msg)
                      complete((StatusCodes.Unauthorized, msg))
                    }

                  case Success(None) =>
                    val msg = s"User not found for accesskey: ${s3Request.credential.accessKey.value}"
                    logger.warn(msg)
                    complete((StatusCodes.Unauthorized, msg))

                  case Failure(exception) =>
                    logger.error("An error occurred retrieving the User from STS service", exception)
                    complete(StatusCodes.InternalServerError)
                }

              case Success(false) =>
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
}
