package com.ing.wbaa.gargoyle.proxy.provider.Atlas

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{ Authorization, BasicHttpCredentials }
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import akka.util.ByteString
import com.ing.wbaa.gargoyle.proxy.provider.Atlas.Model.{ createResponse, updateResponse }
import spray.json.{ JsValue, _ }

import scala.concurrent.Future

class RestClient()(implicit system: ActorSystem) extends AtlasModelJsonSupport {

  implicit val mat = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  //todo: add from config
  private val http = Http(system)
  private val atlasApiUriV1 = Uri("http://localhost:21000/api/atlas")
  private val atlasApiUriV2 = Uri("http://localhost:21000/api/atlas/v2")
  private val bulkEntity = "/entity/bulk"
  private val username = "admin"
  private val password = "admin"

  private val authHeader = Authorization(BasicHttpCredentials(username, password))

  // remove?
  def getTypeGUID(typeName: String, value: String): Future[String] = {
    http.singleRequest(HttpRequest(
      HttpMethods.GET,
      atlasApiUriV1 + s"/entities?type=${typeName}&property=qualifiedName&value=${value}"
    ).withHeaders(authHeader))
      .flatMap { case HttpResponse(_, _, entity, _) => entity.dataBytes.runFold(ByteString(""))(_ ++ _).map(b => b.utf8String) }
  }

  def postData(json: JsValue): Future[String] = {
    http.singleRequest(HttpRequest(
      HttpMethods.POST,
      atlasApiUriV2 + bulkEntity,
      Nil,
      HttpEntity(ContentTypes.`application/json`, json.toString)
    ).withHeaders(authHeader))
      .flatMap { response =>
        Unmarshal(response.entity).to[String].map { jsonString =>
          if (jsonString.contains("CREATE")) {
            jsonString.parseJson.convertTo[createResponse].guidAssignments.convertTo[Map[String, String]].values.toList.head
          } else {
            jsonString.parseJson.convertTo[updateResponse].guidAssignments.convertTo[Map[String, String]].values.toList.head
          }
        }
      }
  }
}
