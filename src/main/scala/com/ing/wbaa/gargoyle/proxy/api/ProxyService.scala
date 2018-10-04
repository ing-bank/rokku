package com.ing.wbaa.gargoyle.proxy.api

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse, RemoteAddress, StatusCodes }
import akka.http.scaladsl.server.{ AuthorizationFailedRejection, Route }
import com.ing.wbaa.gargoyle.proxy.api.directive.ProxyDirectives
import com.ing.wbaa.gargoyle.proxy.config.GargoyleAtlasSettings
import com.ing.wbaa.gargoyle.proxy.data._
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
  protected[this] def executeRequest(request: HttpRequest, clientAddress: RemoteAddress, userSTS: User): Future[HttpResponse]

  // Authentication methods
  protected[this] def areCredentialsActive(awsRequestCredential: AwsRequestCredential): Future[Option[User]]

  // TODO: Implement request authentication using aws signing
  protected[this] def isUserAuthenticated(httpRequest: HttpRequest, awsSecretKey: AwsSecretKey): Boolean

  // Authorization methods
  protected[this] def isUserAuthorizedForRequest(request: S3Request, user: User): Boolean

  // Atlas Lineage
  protected[this] def atlasSettings: GargoyleAtlasSettings
  protected[this] def createLineageFromRequest(httpRequest: HttpRequest, userSTS: User): Future[LineagePostGuidResponse]

  val proxyServiceRoute: Route =
    withoutSizeLimit {
      extractClientIP { remoteAddress =>
        extractRequest { httpRequest =>
          extracts3Request { s3Request =>

            onComplete(areCredentialsActive(s3Request.credential)) {
              case Success(Some(userSTS: User)) =>
                logger.debug(s"Credentials active for request, user retrieved: $userSTS")

                if (isUserAuthenticated(httpRequest, userSTS.secretKey)) {
                  logger.debug(s"Request authenticated: $httpRequest")

                  if (isUserAuthorizedForRequest(s3Request, userSTS)) {
                    logger.info(s"User (${userSTS.userName}) successfully authorized for request: $s3Request")
                    complete(executeRequest(httpRequest, remoteAddress, userSTS).map { request =>
                      if (atlasSettings.atlasEnabled && (request.status == StatusCodes.OK || request.status == StatusCodes.NoContent))
                        // delete on AWS response 204
                        createLineageFromRequest(httpRequest, userSTS)

                      request
                    })
                  } else {
                    logger.warn(s"User (${userSTS.userName}) not authorized for request: $s3Request")
                    reject(AuthorizationFailedRejection)
                  }
                } else {
                  logger.warn(s"Request not authenticated: $httpRequest")
                  complete(StatusCodes.Forbidden)
                }

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
}
