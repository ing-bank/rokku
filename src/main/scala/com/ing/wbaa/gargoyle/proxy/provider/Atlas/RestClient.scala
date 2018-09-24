package com.ing.wbaa.gargoyle.proxy.provider.Atlas

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{ Authorization, BasicHttpCredentials }
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.{ ActorMaterializer, Materializer }
import com.ing.wbaa.gargoyle.proxy.config.GargoyleAtlasSettings
import com.ing.wbaa.gargoyle.proxy.provider.Atlas.Model.{ EntityId, EntitySearchResult, createResponse, updateResponse }
import com.typesafe.scalalogging.LazyLogging
import spray.json.{ JsValue, _ }

import scala.concurrent.Future

class RestClient()(implicit system: ActorSystem, atlasSettings: GargoyleAtlasSettings)
  extends AtlasModelJsonSupport with LazyLogging {

  implicit val materializer: Materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  private val http = Http(system)
  private val atlasApiUriV1 = atlasSettings.atlasBaseUri + "/api/atlas"
  private val atlasApiUriV2 = atlasSettings.atlasBaseUri + "/api/atlas/v2"
  private val bulkEntity = "/entity/bulk"
  private val entityGuid = "/entity/guid"
  private val username = atlasSettings.atlasApiUser
  private val password = atlasSettings.atlasApiPassword

  private val authHeader = Authorization(BasicHttpCredentials(username, password))

  def getEntityGUID(typeName: String, value: String): Future[String] = {
    http.singleRequest(HttpRequest(
      HttpMethods.GET,
      atlasApiUriV1 + s"/entities?type=${typeName}&property=qualifiedName&value=${value}"
    ).withHeaders(authHeader))
      .flatMap { response =>
        Unmarshal(response.entity).to[String].map { jsonString =>
          val searchResult = jsonString.parseJson.convertTo[EntitySearchResult]
          logger.debug("Atlas RestClient: Extracted GUID: " + searchResult.definition.getFields("id").toList.head.convertTo[EntityId].id)
          searchResult.definition.getFields("id").toList.head.convertTo[EntityId].id
        }
      }
  }

  def deleteEntity(guid: String): Future[String] = {
    http.singleRequest(HttpRequest(
      HttpMethods.DELETE,
      atlasApiUriV2 + s"$entityGuid/$guid"
    ).withHeaders(authHeader))
      .flatMap { response =>
        val delStatus = Unmarshal(response.entity).to[String]
        logger.debug("Atlas RestClient: Deleting Entity: " + delStatus)
        delStatus
      }
  }

  def postData(json: JsValue): Future[String] = {
    //def parseGuidFrom[T <: GuidResponse](jsonString: String, typeName: T): String = jsonString.parseJson.convertTo[typeName.type].guidAssignments.convertTo[Map[String, String]].values.toList.head

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
