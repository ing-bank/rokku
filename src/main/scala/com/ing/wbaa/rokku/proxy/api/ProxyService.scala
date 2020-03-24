package com.ing.wbaa.rokku.proxy.api

import java.util.UUID

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.marshallers.xml.ScalaXmlSupport._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server._
import akka.stream.ActorMaterializer
import com.ing.wbaa.rokku.proxy.api.directive.ProxyDirectives
import com.ing.wbaa.rokku.proxy.data._
import com.ing.wbaa.rokku.proxy.handler.FilterRecursiveMultiDelete.exctractMultideleteObjectsFlow
import com.ing.wbaa.rokku.proxy.handler.LoggerHandlerWithId
import com.ing.wbaa.rokku.proxy.handler.exception.RokkuThrottlingException
import com.ing.wbaa.rokku.proxy.handler.parsers.RequestParser.AWSRequestType
import com.ing.wbaa.rokku.proxy.persistence.HttpRequestRecorder.ExecutedRequestCmd
import com.ing.wbaa.rokku.proxy.provider.aws.AwsErrorCodes

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

  protected[this] def auditLog(s3Request: S3Request, httpRequest: HttpRequest, user: String, awsRequest: AWSRequestType, responseStatus: StatusCode = StatusCodes.Processing)(implicit id: RequestId): Future[Done]

  protected[this] def awsRequestFromRequest(request: HttpRequest): AWSRequestType

  val requestPersistenceEnabled: Boolean
  val configuredPersistenceId: String

  implicit def rokkuExceptionHandler: ExceptionHandler =
    ExceptionHandler {
      case _: RokkuThrottlingException =>
        complete(StatusCodes.ServiceUnavailable -> AwsErrorCodes.response(StatusCodes.ServiceUnavailable))
    }

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
                    implicit val returnStatusCode: StatusCodes.ClientError = StatusCodes.Forbidden
                    logger.error("An error occurred while processing request for valid user ex={}", exception)
                    complete(returnStatusCode -> AwsErrorCodes.response(returnStatusCode))
                }
              case Success(None) =>
                implicit val returnStatusCode: StatusCodes.ClientError = StatusCodes.Forbidden
                logger.warn("STS credentials not active: {}", s3Request)
                complete(returnStatusCode -> AwsErrorCodes.response(returnStatusCode))
              case Failure(exception) =>
                implicit val returnStatusCode: StatusCodes.ServerError = StatusCodes.InternalServerError
                logger.error("An error occurred when checking credentials with STS service ex={}", exception)
                complete(returnStatusCode -> AwsErrorCodes.response(returnStatusCode))
            }
          }
        }
      }
    }

  private def checkExtractedPostContents(httpRequest: HttpRequest, s3Request: S3Request, userSTS: User)(implicit id: RequestId): Future[Route] = {
    import scala.concurrent.duration._
    implicit val mat = ActorMaterializer()

    // we need to materialize here to avoid error - failed to materialize stream more than once, when http client in handler will
    // try to send the same entity to s3
    // also this prevents line spits to chunks for large requests
    httpRequest.entity.toStrict(3.seconds).flatMap { strictE =>
      exctractMultideleteObjectsFlow(strictE.dataBytes).map { s3Objects =>
        s3Objects.map { s3Object =>
          val bucket = s3Request.s3BucketPath.getOrElse("")
          isUserAuthorizedForRequest(s3Request.copy(s3BucketPath = Some(s"$bucket/$s3Object"), s3Object = Some(s3Object)), userSTS)
        }
      }.map { permittedObjects =>
        if (permittedObjects.nonEmpty && permittedObjects.contains(false)) {
          implicit val returnStatusCode: StatusCodes.ClientError = StatusCodes.Forbidden
          logger.warn("Multidelete - one of objects not allowed to be accessed")
          complete(returnStatusCode -> AwsErrorCodes.response(returnStatusCode))
        } else {
          logger.info(s"User (${userSTS.userName}) successfully authorized for multidelete request: $s3Request")
          processAuthorizedRequest(httpRequest.withEntity(strictE), s3Request, userSTS)
        }
      }
    }
  }

  protected[this] def processAuthorizedRequest(httpRequest: HttpRequest, s3Request: S3Request, userSTS: User)(implicit id: RequestId): Route = {
    updateHeadersForRequest(httpRequest) { newHttpRequest =>
      val httpResponse = executeRequest(newHttpRequest, userSTS, s3Request).andThen {
        case Success(response: HttpResponse) =>
          //add request recording after getting response and before executing postrequest actions, we skip ls requests
          val isListRequest = httpRequest.method.value == "GET" && httpRequest.uri.rawQueryString.getOrElse("empty").contains("prefix")
          if (requestPersistenceEnabled && !isListRequest && response.status == StatusCodes.OK) {
            lazy val lineageRecorderRef = system.actorSelection(s"/user/$configuredPersistenceId")
            lineageRecorderRef ! ExecutedRequestCmd(httpRequest, userSTS, s3Request.clientIPAddress)
          }
          handlePostRequestActions(response, httpRequest, s3Request, userSTS)
      }
      complete(httpResponse)
    }
  }

  private def processRequestForValidUser(httpRequest: HttpRequest, s3Request: S3Request, userSTS: User)(implicit id: RequestId) = {
    auditLog(s3Request, httpRequest, userSTS.userName.value, awsRequestFromRequest(httpRequest)).andThen({
      case Failure(err) => logger.error(s"Error while sending audit log: ${err}")
    })
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
        implicit val returnStatusCode: StatusCodes.ClientError = StatusCodes.Unauthorized
        logger.warn(s"User (${userSTS.userName}) not authorized for request: $s3Request")
        auditLog(s3Request, httpRequest, userSTS.userName.value, awsRequestFromRequest(httpRequest), returnStatusCode).andThen({
          case Failure(err) => logger.error(s"Error while sending audit log: ${err}")
        })
        Future.successful(complete(returnStatusCode -> AwsErrorCodes.response(returnStatusCode)))
      }
    } else {
      implicit val returnStatusCode: StatusCodes.ClientError = StatusCodes.Forbidden
      logger.warn("Request not authenticated: {}", httpRequest)
      auditLog(s3Request, httpRequest, userSTS.userName.value, awsRequestFromRequest(httpRequest), returnStatusCode).andThen({
        case Failure(err) => logger.error(s"Error while sending audit log: ${err}")
      })
      Future.successful(complete(returnStatusCode -> AwsErrorCodes.response(returnStatusCode)))
    }
  }
}
