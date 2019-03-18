package com.ing.wbaa.airlock.proxy.api

import akka.actor.ActorSystem
import akka.http.scaladsl.marshallers.xml.ScalaXmlSupport._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import com.ing.wbaa.airlock.proxy.api.directive.ProxyDirectives
import com.ing.wbaa.airlock.proxy.data._
import com.ing.wbaa.airlock.proxy.provider.aws.AwsErrorCodes
import com.ing.wbaa.airlock.proxy.handler.FilterRecursiveMultiDelete._
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
  protected[this] def executeRequest(request: HttpRequest, userSTS: User, s3request: S3Request): Future[HttpResponse]

  // Authentication methods
  protected[this] def areCredentialsActive(awsRequestCredential: AwsRequestCredential): Future[Option[User]]

  // AWS Signature methods
  protected[this] def isUserAuthenticated(httpRequest: HttpRequest, awsSecretKey: AwsSecretKey): Boolean

  // Authorization methods
  protected[this] def isUserAuthorizedForRequest(request: S3Request, user: User): Boolean

  protected[this] def handlePostRequestActions(response: HttpResponse, httpRequest: HttpRequest, s3Request: S3Request, userSTS: User): Unit

  val proxyServiceRoute: Route =
    metricDuration {
      withoutSizeLimit {
        extractRequest { httpRequest =>
          extracts3Request { s3Request =>
            onComplete(areCredentialsActive(s3Request.credential)) {
              case Success(Some(userSTS: User)) =>
                logger.debug(s"Credentials active for request, user retrieved: $userSTS")
                onComplete(processRequestForValidUser(httpRequest, s3Request, userSTS)) {
                  case Success(r) => r
                  case Failure(exception) =>
                    logger.error(s"An error occurred while checking authentication", exception)
                    complete(StatusCodes.Forbidden -> AwsErrorCodes.response(StatusCodes.Forbidden))
                }
              case Success(None) =>
                val msg = s"Request not authenticated: $s3Request"
                logger.warn(msg)
                complete(StatusCodes.Forbidden -> AwsErrorCodes.response(StatusCodes.Forbidden))
              case Failure(exception) =>
                logger.error(s"An error occurred checking authentication with STS service", exception)
                complete(StatusCodes.InternalServerError -> AwsErrorCodes.response(StatusCodes.InternalServerError))
            }
          }
        }
      }
    }

  private def checkExtractedPostContents(httpRequest: HttpRequest, s3Request: S3Request, userSTS: User): Future[Route] =
    exctractMultideleteObjectsFlow(httpRequest.entity.dataBytes)(ActorMaterializer()).map { s3Objects =>
      s3Objects.map { s3Object =>
        val bucket = s3Request.s3BucketPath.getOrElse("")
        isUserAuthorizedForRequest(s3Request.copy(s3BucketPath = Some(s"$bucket/$s3Object"), s3Object = Some(s3Object)), userSTS)
      }
    }.map { permittedObjects =>
      if (permittedObjects.length > 0 && permittedObjects.contains(false)) {
        logger.debug("An error occurred, one of objects not allowed to be accessed")
        complete(StatusCodes.Forbidden -> AwsErrorCodes.response(StatusCodes.Forbidden))
      } else {
        logger.info(s"User (${userSTS.userName}) successfully authorized for request: $s3Request")
        processAuthorizedRequest(httpRequest, s3Request, userSTS)
      }
    }

  protected[this] def processAuthorizedRequest(httpRequest: HttpRequest, s3Request: S3Request, userSTS: User): Route = {
    updateHeadersForRequest { newHttpRequest =>
      val httpResponse = executeRequest(newHttpRequest, userSTS, s3Request).andThen {
        case Success(response: HttpResponse) =>
          handlePostRequestActions(response, httpRequest, s3Request, userSTS)
      }
      complete(httpResponse)
    }
  }

  private def processRequestForValidUser(httpRequest: HttpRequest, s3Request: S3Request, userSTS: User) = {
    if (isUserAuthenticated(httpRequest, userSTS.secretKey)) {
      logger.debug(s"Request authenticated: $httpRequest")
      if (isUserAuthorizedForRequest(s3Request, userSTS)) {
        // if request is multidelete post
        if (httpRequest.entity.contentType.mediaType == MediaTypes.`application/xml` && httpRequest.method == HttpMethods.POST) {
          checkExtractedPostContents(
            httpRequest,
            s3Request.copy(mediaType = MediaTypes.`application/xml`), userSTS)
        } else {
          logger.info(s"User (${userSTS.userName}) successfully authorized for request: $s3Request")
          Future(processAuthorizedRequest(httpRequest, s3Request, userSTS))
        }
      } else {
        logger.warn(s"User (${userSTS.userName}) not authorized for request: $s3Request")
        Future.successful(complete(StatusCodes.Forbidden -> AwsErrorCodes.response(StatusCodes.Forbidden)))
      }
    } else {
      logger.warn(s"Request not authenticated: $httpRequest")
      Future.successful(complete(StatusCodes.Forbidden -> AwsErrorCodes.response(StatusCodes.Forbidden)))
    }
  }
}
