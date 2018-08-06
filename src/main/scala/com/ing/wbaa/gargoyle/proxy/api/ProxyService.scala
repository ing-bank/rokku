package com.ing.wbaa.gargoyle.proxy.api

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse, RemoteAddress, StatusCodes }
import akka.http.scaladsl.server.Route
import com.ing.wbaa.gargoyle.proxy.api.directive.ProxyDirectives
import com.ing.wbaa.gargoyle.proxy.data.{ AwsAccessKey, AwsRequestCredential, S3Request, User }
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Future
import scala.util.{ Failure, Success }

trait ProxyService extends LazyLogging {

  // no validation of request currently
  // once we get comfortable with get/put/del we can add permCheck
  import ProxyDirectives._
  import akka.http.scaladsl.server.Directives._

  implicit def system: ActorSystem

  // Request Handler methods
  def validateUserRequest(request: HttpRequest, secretKey: String): Boolean
  def executeRequest(request: HttpRequest, clientAddress: RemoteAddress): Future[HttpResponse]

  // Authentication methods
  def getUser(accessKey: AwsAccessKey): Future[Option[User]]
  def isAuthenticated(awsRequestCredential: AwsRequestCredential): Future[Boolean]

  // Authorization methods
  def isAuthorized(request: S3Request, user: User): Boolean

  val proxyServiceRoute: Route =
    withoutSizeLimit {
      extractClientIP { remoteAddress =>
        extractRequest { httpRequest =>
          extracts3Request { s3Request =>

            // Check whether the request is authenticated
            onComplete(isAuthenticated(s3Request.credential)) {
              case Success(true) =>
                logger.debug(s"Request authenticated: $s3Request")

                // Find the user corresponding to the received request
                onComplete(getUser(s3Request.credential.accessKey)) {
                  case Success(Some(user: User)) =>
                    logger.debug(s"User retrieved: $user")

                    if (validateUserRequest(httpRequest, user.secretKey)) {
                      if (isAuthorized(s3Request, user)) {
                        logger.info(s"User (${user.userId}) successfully authorized for request: $s3Request")
                        complete(executeRequest(httpRequest, remoteAddress))
                      } else {
                        val msg = s"User (${user.userId}) not authorized for request: $s3Request"
                        logger.warn(msg)
                        complete((StatusCodes.Unauthorized, msg))
                      }

                    } else {
                      val msg = s"Request could not be validated: $httpRequest"
                      logger.warn(msg)
                      complete((StatusCodes.BadRequest, msg))
                    }

                  case Success(None) =>
                    val msg = s"User not found for accesskey: ${s3Request.credential.accessKey.value}"
                    logger.warn(msg)
                    complete((StatusCodes.Unauthorized, msg))

                  case Failure(exception) =>
                    logger.error("Exception occurred: ", exception)
                    complete(
                      (StatusCodes.InternalServerError,
                        s"An error occurred retrieving the User from STS service: ${exception.getMessage}")
                    )
                }

              case Success(false) =>
                val msg = s"Request not authenticated: $s3Request"
                logger.warn(msg)
                complete((StatusCodes.Forbidden, msg))

              case Failure(exception) =>
                logger.error("Exception occurred: ", exception)
                complete(
                  (StatusCodes.InternalServerError,
                    s"An error occurred checking authentication with STS service: ${exception.getMessage}")
                )
            }
          }
        }
      }
    }
}
