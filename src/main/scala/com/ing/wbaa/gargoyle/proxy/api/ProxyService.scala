package com.ing.wbaa.gargoyle.proxy.api

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Route
import com.ing.wbaa.gargoyle.proxy.data.{AccessType, S3Request, User}
import com.ing.wbaa.gargoyle.proxy.handler.RequestHandlerBase
import com.ing.wbaa.gargoyle.proxy.providers.{AuthenticationProviderBase, AuthorizationProviderBase}
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait ProxyService extends LazyLogging
  with AuthenticationProviderBase
  with AuthorizationProviderBase
  with RequestHandlerBase {

  implicit val system: ActorSystem

  // no validation of request currently
  // once we get comfortable with get/put/del we can add permCheck
  import akka.http.scaladsl.server.Directives._

  val proxyServiceRoute: Route =
    withoutSizeLimit {
      extractClientIP { remoteAddress =>
        extractRequest { httpRequest =>
          optionalHeaderValueByName("x-amz-security-token") { sessionToken =>
            headerValueByName("authorization") { authorizationHeader =>
              logger.debug(authorizationHeader)
              val credential =
                """Credential=([\w,/]*), """.r
                  .findFirstMatchIn(authorizationHeader)
                  .map(_ group 1)

              logger.debug(credential.toString)

              val response = credential.map(_.split("/")(0)).fold(Future(HttpResponse(StatusCodes.Forbidden))) {
                accessKey => {
                  def handleGetUser(s3Request: S3Request): Future[Either[User, HttpResponse]] =
                    getUser(s3Request.accessKey)
                      .map {
                        case Some(user) => Left(user)
                        case _ => Right(HttpResponse(StatusCodes.Unauthorized))
                      }

                  def handleAuthentication(s3Request: S3Request): Future[Either[Unit, HttpResponse]] =
                    isAuthenticated(s3Request.accessKey, s3Request.sessionToken).map {
                      case false => Right(HttpResponse(StatusCodes.Forbidden))
                      case true => Left(Unit)
                    }

                  def handleValidation(httpRequest: HttpRequest, user: User): Either[User, HttpResponse] =
                    if (validateUserRequest(httpRequest, user.secretKey)) Left(user)
                    else Right(HttpResponse(StatusCodes.BadRequest))

                  def handleAuthorization(s3Request: S3Request, user: User): Either[User, HttpResponse] =
                    if (isAuthorized(s3Request, user)) Left(user)
                    else Right(HttpResponse(StatusCodes.Unauthorized))

                  logger.debug(s"Going to translate: $httpRequest")
                  val bucket = {
                    val firstPath = httpRequest.uri.path.tail.toString
                    if (firstPath.isEmpty) None else Some(firstPath)
                  }
                  val accessType = if (httpRequest.method == HttpMethods.GET) AccessType.read else AccessType.write

                  val s3Request = new S3Request(accessKey, sessionToken, bucket, accessType)
                  logger.debug(s"Extracted S3 Request: $s3Request")

                  handleAuthentication(s3Request)
                    .flatMap {
                      case Left(_) => handleGetUser(s3Request)
                      case Right(r) => Future(Right(r))
                    }
                    .map {
                      case Left(user) => handleValidation(httpRequest, user)
                      case Right(r) => Right(r)
                    }
                    .map {
                      case Left(user) => handleAuthorization(s3Request, user)
                      case Right(r) => Right(r)
                    }
                    .flatMap {
                      case Left(_) => executeRequest(httpRequest, remoteAddress)
                      case Right(r) => Future(r)
                    }
                }
              }
              complete(response)
            }
          }
        }
      }
    }
}
