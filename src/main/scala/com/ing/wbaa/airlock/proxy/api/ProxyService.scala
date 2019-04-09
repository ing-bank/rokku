package com.ing.wbaa.airlock.proxy.api

import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.marshallers.xml.ScalaXmlSupport._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import com.ing.wbaa.airlock.proxy.api.directive.ProxyDirectives
import com.ing.wbaa.airlock.proxy.data._
import com.ing.wbaa.airlock.proxy.handler.LoggerHandlerWithId
import com.ing.wbaa.airlock.proxy.provider.aws.AwsErrorCodes
import com.ing.wbaa.airlock.proxy.handler.FilterRecursiveMultiDelete._

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

trait ProxyService {

  private val logger = new LoggerHandlerWithId

  // no validation of request currently
  // once we get comfortable with get/put/del we can add permCheck
  import ProxyDirectives._
  import akka.http.scaladsl.server.Directives._

  protected[this] implicit def system: ActorSystem

  protected[this] implicit def executionContext: ExecutionContext

  // Request Handler methods
  protected[this] def executeRequest(request: HttpRequest, userSTS: User, s3request: S3Request)(implicit id: RequestId): Future[HttpResponse]

  // Authentication methods
  protected[this] def areCredentialsActive(awsRequestCredential: AwsRequestCredential)(implicit id: RequestId): Future[Option[User]]

  // AWS Signature methods
  protected[this] def isUserAuthenticated(httpRequest: HttpRequest, awsSecretKey: AwsSecretKey)(implicit id: RequestId): Boolean

  // Authorization methods
  protected[this] def isUserAuthorizedForRequest(request: S3Request, user: User)(implicit id: RequestId): Boolean

  protected[this] def handlePostRequestActions(response: HttpResponse, httpRequest: HttpRequest, s3Request: S3Request, userSTS: User)(implicit id: RequestId): Unit

  val proxyServiceRoute: Route =

    metricDuration {
      implicit val requestId: RequestId = RequestId(UUID.randomUUID().toString)
      withoutSizeLimit {
        extractRequest { httpRequest =>
          extracts3Request { s3Request =>
            onComplete(areCredentialsActive(s3Request.credential)) {
              case Success(Some(userSTS: User)) =>
                logger.info("STS credentials active for request, user retrieved: {}", userSTS)
                onComplete(processRequestForValidUser(httpRequest, s3Request, userSTS)) {
                  case Success(r) => r
                  case Failure(exception) =>
                    logger.error("An error occurred while processing request for valid user", exception)
                    complete(StatusCodes.Forbidden -> AwsErrorCodes.response(StatusCodes.Forbidden))
                }
              case Success(None) =>
                logger.warn("STS credentials not active: {}", s3Request)
                complete(StatusCodes.Forbidden -> AwsErrorCodes.response(StatusCodes.Forbidden))
              case Failure(exception) =>
                logger.error("An error occurred when checking credentials with STS service", exception)
                complete(StatusCodes.InternalServerError -> AwsErrorCodes.response(StatusCodes.InternalServerError))
            }
          }
        }
      }
    }

  private def checkExtractedPostContents(httpRequest: HttpRequest, s3Request: S3Request, userSTS: User)(implicit id: RequestId): Future[Route] =
    exctractMultideleteObjectsFlow(httpRequest.entity.dataBytes)(ActorMaterializer()).map { s3Objects =>
      s3Objects.map { s3Object =>
        val bucket = s3Request.s3BucketPath.getOrElse("")
        isUserAuthorizedForRequest(s3Request.copy(s3BucketPath = Some(s"$bucket/$s3Object"), s3Object = Some(s3Object)), userSTS)
      }
    }.map { permittedObjects =>
      if (permittedObjects.nonEmpty && permittedObjects.contains(false)) {
        logger.warn("Multidelete - one of objects not allowed to be accessed")
        complete(StatusCodes.Forbidden -> AwsErrorCodes.response(StatusCodes.Forbidden))
      } else {
        logger.info(s"User (${userSTS.userName}) successfully authorized for multidelete request: $s3Request")
        processAuthorizedRequest(httpRequest, s3Request, userSTS)
      }
    }

  protected[this] def processAuthorizedRequest(httpRequest: HttpRequest, s3Request: S3Request, userSTS: User)(implicit id: RequestId): Route = {
    updateHeadersForRequest { newHttpRequest =>
      val httpResponse = executeRequest(newHttpRequest, userSTS, s3Request).andThen {
        case Success(response: HttpResponse) =>
          handlePostRequestActions(response, httpRequest, s3Request, userSTS)
      }
      complete(httpResponse)
    }
  }

  private def processRequestForValidUser(httpRequest: HttpRequest, s3Request: S3Request, userSTS: User)(implicit id: RequestId) = {
    if (isUserAuthenticated(httpRequest, userSTS.secretKey)) {
      logger.info("Request authenticated: {}", httpRequest)
      if (isUserAuthorizedForRequest(s3Request, userSTS)) {
        val rawQueryString = httpRequest.uri.rawQueryString.getOrElse("")
        val isMultideletePost =
          (httpRequest.entity.contentType.mediaType == MediaTypes.`application/xml` || httpRequest.entity.contentType.mediaType == MediaTypes.`application/octet-stream`) &&
            httpRequest.method == HttpMethods.POST && rawQueryString == "delete"

        if (isMultideletePost) {
          checkExtractedPostContents(
            httpRequest,
            s3Request.copy(mediaType = MediaTypes.`application/xml`, accessType = Write("MULTIDELETE POST")), userSTS)
        } else {
          logger.info(s"User (${userSTS.userName}) successfully authorized for request: $s3Request")
          Future(processAuthorizedRequest(httpRequest, s3Request, userSTS))
        }
      } else {
        logger.warn(s"User (${userSTS.userName}) not authorized for request: $s3Request")
        Future.successful(complete(StatusCodes.Forbidden -> AwsErrorCodes.response(StatusCodes.Forbidden)))
      }
    } else {
      logger.warn("Request not authenticated: {}", httpRequest)
      Future.successful(complete(StatusCodes.Forbidden -> AwsErrorCodes.response(StatusCodes.Forbidden)))
    }
  }
}
