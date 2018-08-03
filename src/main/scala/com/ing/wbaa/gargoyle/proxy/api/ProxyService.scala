package com.ing.wbaa.gargoyle.proxy.api

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Route
import com.ing.wbaa.gargoyle.proxy.api.directive.ProxyDirectives
import com.ing.wbaa.gargoyle.proxy.data.User
import com.ing.wbaa.gargoyle.proxy.handler.RequestHandlerBase
import com.ing.wbaa.gargoyle.proxy.providers.{AuthenticationProviderBase, AuthorizationProviderBase}
import com.typesafe.scalalogging.LazyLogging

import scala.util.{Failure, Success}

trait ProxyService extends LazyLogging
  with AuthenticationProviderBase
  with AuthorizationProviderBase
  with RequestHandlerBase {

  implicit val system: ActorSystem

  // no validation of request currently
  // once we get comfortable with get/put/del we can add permCheck
  import ProxyDirectives._
  import akka.http.scaladsl.server.Directives._

  val proxyServiceRoute: Route =
    withoutSizeLimit {
      extractClientIP { remoteAddress =>
        extractRequest { httpRequest =>
          extracts3Request { s3Request =>

            // Check whether the request is authenticated
            onComplete(isAuthenticated(s3Request.accessKey, s3Request.sessionToken)) {
              case Success(true) =>
                logger.debug(s"Request authenticated: $s3Request")

                // Find the user corresponding to the received request
                onComplete(getUser(s3Request.accessKey)) {
                  case Success(Some(user: User)) =>
                    logger.debug(s"User retrieved: $user")

                    if (validateUserRequest(httpRequest, user.secretKey)) {
                      if(isAuthorized(s3Request, user)) {
                        logger.debug(s"User ($user) successfully authorized for request: $s3Request")
                        complete(executeRequest(httpRequest, remoteAddress))
                      } else {
                        logger.debug(s"User ($user) not authorized for request: $s3Request")
                        complete(HttpResponse(StatusCodes.Unauthorized))
                      }

                    } else {
                      logger.debug(s"Request could not be validated: $httpRequest")
                      complete(HttpResponse(StatusCodes.BadRequest))
                    }

                  case Success(None) =>
                    logger.debug(s"User not found for accesskey: ${s3Request.accessKey}")
                    complete(HttpResponse(StatusCodes.Unauthorized))

                  case Failure(exception) =>
                    logger.error("Exception occurred: ", exception)
                    complete(
                    (StatusCodes.InternalServerError,
                      s"An error occurred retrieving the User from STS service: ${exception.getMessage}")
                  )
                }

              case Success(false) =>
                logger.debug(s"Request not authenticated: $s3Request")
                complete(HttpResponse(StatusCodes.Forbidden))

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
