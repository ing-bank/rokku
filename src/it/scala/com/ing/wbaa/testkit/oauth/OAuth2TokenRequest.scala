package com.ing.wbaa.testkit.oauth

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink

import scala.concurrent.{ExecutionContextExecutor, Future}

case class KeycloackToken(access_token: String)

/**
  * OAuth2 request for token
  */
trait OAuth2TokenRequest {

  protected implicit def system: ActorSystem

  protected implicit def materializer: ActorMaterializer

  protected implicit def exContext: ExecutionContextExecutor

  protected[this] def gargoyleKeycloakTokenUrl: String


  import spray.json._
  import DefaultJsonProtocol._

  private implicit val keycloakTokenJson: RootJsonFormat[KeycloackToken] = jsonFormat1(KeycloackToken)


  private def getTokenResponse(formData: Map[String, String]): Future[HttpResponse] = {
    Http().singleRequest(HttpRequest(
      uri = Uri(gargoyleKeycloakTokenUrl),
      method = HttpMethods.POST,
      entity = akka.http.scaladsl.model.FormData(formData).toEntity(HttpCharsets.`UTF-8`)))
  }

  def keycloackToken(formData: Map[String, String]): Future[KeycloackToken] =
    getTokenResponse(formData).map(_.entity.dataBytes.map(_.utf8String)
      .map(_.parseJson.convertTo[KeycloackToken])
      .runWith(Sink.seq)).flatMap(_.map(_.head)).recover { case _ => KeycloackToken("invalid") }

}
