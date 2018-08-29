package com.ing.wbaa.testkit.oauth

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink

import scala.concurrent.{ExecutionContext, Future}

case class KeycloackToken(access_token: String)

/**
  * OAuth2 request for token
  */
trait OAuth2TokenRequest {

  protected[this] implicit def testSystem: ActorSystem

  protected[this] implicit def materializer: ActorMaterializer

  protected[this] implicit def executionContext: ExecutionContext

  private[this] def gargoyleKeycloakTokenUrl: String = testSystem.settings.config.getString("gargoyle.sts.keycloak.token.url")

  import spray.json._
  import DefaultJsonProtocol._

  private[this] implicit val keycloakTokenJson: RootJsonFormat[KeycloackToken] = jsonFormat1(KeycloackToken)

  private[this] def getTokenResponse(formData: Map[String, String]): Future[HttpResponse] = {
    Http().singleRequest(HttpRequest(
      uri = Uri(gargoyleKeycloakTokenUrl),
      method = HttpMethods.POST,
      entity = akka.http.scaladsl.model.FormData(formData).toEntity(HttpCharsets.`UTF-8`)))
  }

  protected[this] def retrieveKeycloackToken(formData: Map[String, String]): Future[KeycloackToken] =
    getTokenResponse(formData).map(_.entity.dataBytes.map(_.utf8String)
      .map(_.parseJson.convertTo[KeycloackToken])
      .runWith(Sink.seq)).flatMap(_.map(_.head)).recover { case _ => KeycloackToken("invalid") }
}
