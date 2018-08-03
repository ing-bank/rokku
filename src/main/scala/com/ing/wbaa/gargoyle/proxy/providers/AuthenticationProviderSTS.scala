package com.ing.wbaa.gargoyle.proxy.providers

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, StatusCodes}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.{ActorMaterializer, Materializer}
import com.ing.wbaa.gargoyle.proxy.config.GargoyleStsSettings
import com.ing.wbaa.gargoyle.proxy.data.{JsonProtocols, User}
import com.typesafe.scalalogging.LazyLogging
import spray.json._

import scala.concurrent.{ExecutionContext, Future}

trait AuthenticationProviderSTS extends AuthenticationProviderBase with JsonProtocols with LazyLogging {

  val stsSettings: GargoyleStsSettings

  implicit val system: ActorSystem

  implicit val executionContext: ExecutionContext

  implicit val materializer: Materializer = ActorMaterializer()

  private[this] lazy val baseUrl = s"http://${stsSettings.stsHost}:${stsSettings.stsPort}"

  override def getUser(accessKey: String): Future[Option[User]] =
    Http()
      .singleRequest(HttpRequest(uri = s"$baseUrl/userInfo?accessKey=$accessKey"))
      .flatMap { response =>
        response.status match {
          case StatusCodes.OK =>
            Unmarshal(response.entity).to[String].map { jsonString =>
              Some(jsonString.parseJson.convertTo[User])
            }
          case StatusCodes.NotFound =>
            logger.error(s"No user could be found for accessKey ($accessKey)")
            Future.successful(None)
          case c =>
            logger.error(s"Received unexpected StatusCode ($c) for accessKey ($accessKey)")
            Future.successful(None)
        }
      }

  override def isAuthenticated(accessKey: String, token: Option[String]): Future[Boolean] =
    token match {
      case None => Future(false)
      case Some(t) =>
        Http()
          .singleRequest(HttpRequest(uri = s"$baseUrl/isCredentialActive?accessKey=$accessKey&sessionToken=$t"))
          .map {
            response =>
              response.status match {
                case StatusCodes.OK => true
                case StatusCodes.Forbidden =>
                  logger.error(s"User not authenticated with accessKey ($accessKey) and sessionToken ($t)")
                  false
                case c =>
                  logger.error(s"Received unexpected StatusCode ($c) for accessKey ($accessKey) and sessionToken ($t)")
                  false
              }
          }
    }
}
