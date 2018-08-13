package com.ing.wbaa.gargoyle.proxy.provider

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ HttpRequest, StatusCodes, Uri }
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import com.ing.wbaa.gargoyle.proxy.config.GargoyleStsSettings
import com.ing.wbaa.gargoyle.proxy.data.{ AwsAccessKey, AwsRequestCredential, JsonProtocols, User }
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{ ExecutionContext, Future }

trait AuthenticationProviderSTS extends JsonProtocols with LazyLogging {

  import spray.json._
  import AuthenticationProviderSTS.STSException

  implicit def system: ActorSystem
  implicit def executionContext: ExecutionContext
  implicit def materializer: Materializer

  def stsSettings: GargoyleStsSettings

  def getUserForAccessKey(accessKey: AwsAccessKey): Future[Option[User]] = {
    val uri = stsSettings.stsBaseUri
      .withPath(Uri.Path("/userInfo"))
      .withQuery(Uri.Query(Map(
        "accessKey" -> accessKey.value
      )))

    Http()
      .singleRequest(HttpRequest(uri = uri))
      .flatMap { response =>
        response.status match {
          case StatusCodes.OK =>
            Unmarshal(response.entity).to[String].map { jsonString =>
              Some(jsonString.parseJson.convertTo[User])
            }
          case StatusCodes.NotFound =>
            logger.error(s"No user could be found for accessKey (${accessKey.value})")
            Future.successful(None)
          case c =>
            val msg = s"Received unexpected StatusCode ($c) for accessKey (${accessKey.value})"
            logger.error(msg)
            Future.failed(STSException(msg))
        }
      }
  }

  def areCredentialsAuthentic(awsRequestCredential: AwsRequestCredential): Future[Boolean] =
    awsRequestCredential.sessionToken match {
      case None => Future(false)
      case Some(sessionToken) =>
        val uri = stsSettings.stsBaseUri
          .withPath(Uri.Path("/isCredentialActive"))
          .withQuery(Uri.Query(Map(
            "accessKey" -> awsRequestCredential.accessKey.value,
            "sessionToken" -> sessionToken.value
          )))

        Http()
          .singleRequest(HttpRequest(uri = uri))
          .map {
            response =>
              response.status match {
                case StatusCodes.OK => true
                case StatusCodes.Forbidden =>
                  logger.error(s"User not authenticated " +
                    s"with accessKey (${awsRequestCredential.accessKey.value}) " +
                    s"and sessionToken (${sessionToken.value})")
                  false
                case c =>
                  logger.error(s"Received unexpected StatusCode ($c) for " +
                    s"accessKey (${awsRequestCredential.accessKey.value}) " +
                    s"and sessionToken (${sessionToken.value})")
                  false
              }
          }
    }
}

object AuthenticationProviderSTS {
  final case class STSException(private val message: String, private val cause: Throwable = None.orNull)
    extends Exception(message, cause)
}
