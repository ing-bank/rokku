package com.ing.wbaa.gargoyle.proxy.provider.Atlas

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{ Authorization, BasicHttpCredentials }
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.{ ActorMaterializer, Materializer }
import com.ing.wbaa.gargoyle.proxy.config.GargoyleAtlasSettings
import com.ing.wbaa.gargoyle.proxy.provider.Atlas.Model.{ EntityId, EntitySearchResult, createResponse, updateResponse }
import com.ing.wbaa.gargoyle.proxy.provider.Atlas.RestClient.RestClientException
import com.typesafe.scalalogging.LazyLogging
import spray.json.{ JsValue, _ }

import scala.concurrent.Future

class RestClient()(implicit system: ActorSystem, atlasSettings: GargoyleAtlasSettings)
  extends AtlasModelJsonSupport with LazyLogging {

  implicit val materializer: Materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  private val http = Http(system)
  private val atlasApiUriV1 = atlasSettings.atlasBaseUri.withPath(Uri.Path("/api/atlas"))
  private val atlasApiUriV2 = atlasSettings.atlasBaseUri.withPath(Uri.Path("/api/atlas/v2"))
  private val bulkEntity = "/entity/bulk"
  private val entityGuid = "/entity/guid"
  private val username = atlasSettings.atlasApiUser
  private val password = atlasSettings.atlasApiPassword

  private val authHeader = Authorization(BasicHttpCredentials(username, password))

  def getEntityGUID(typeName: String, value: String) = {
    http.singleRequest(HttpRequest(
      HttpMethods.GET,
      atlasApiUriV1 + s"/entities?type=${typeName}&property=qualifiedName&value=${value}"
    ).withHeaders(authHeader))
      .flatMap {
        case response if response.status == StatusCodes.OK =>
          Unmarshal(response.entity).to[String].map { jsonString =>
            val searchResult = jsonString.parseJson.convertTo[EntitySearchResult]
            logger.debug("Atlas RestClient: Extracted GUID: " + searchResult.definition.getFields("id").toList.head.convertTo[EntityId].id)
            searchResult.definition.getFields("id").toList.head.convertTo[EntityId].id
          }
        case response =>
          logger.debug(s"Atlas getEntityGUID failed: ${response.status}")
          Future.failed(RestClientException(s"Atlas getEntityGUID failed: ${response.status}"))
      }.recoverWith { case ex =>
        logger.debug(s"Atlas getEntityGUID failed: ${ex.getMessage}")
        Future.failed(RestClientException(s"Atlas getEntityGUID failed: ${ex.getMessage}", ex.getCause))
      }
  }

  def deleteEntity(guid: String): Future[String] = {
    http.singleRequest(HttpRequest(
      HttpMethods.DELETE,
      atlasApiUriV2 + s"$entityGuid/$guid"
    ).withHeaders(authHeader))
      .flatMap {
        case response if response.status == StatusCodes.OK =>
          val deleteResult = Unmarshal(response.entity).to[String]
          logger.debug(s"Atlas RestClient: Deleting Entity: $deleteResult")
          deleteResult
        case response =>
          logger.debug(s"Atlas deleteEntity failed: ${response.status}")
          Future.failed(RestClientException(s"Atlas deleteEntity failed: ${response.status}"))
      }.recoverWith { case ex =>
        logger.debug(s"Atlas deleteEntity failed: ${ex.getMessage}")
        Future.failed(RestClientException(s"Atlas deleteEntity failed: ${ex.getMessage}", ex.getCause))
      }
  }

  // post data will either create or update entity
  def postData(json: JsValue): Future[String] = {
    http.singleRequest(HttpRequest(
      HttpMethods.POST,
      atlasApiUriV2 + bulkEntity,
      Nil,
      HttpEntity(ContentTypes.`application/json`, json.toString)
    ).withHeaders(authHeader))
      .flatMap {
        case response if response.status == StatusCodes.OK =>
          Unmarshal(response.entity).to[String].map { jsonString =>
            if (jsonString.contains("CREATE")) {
              jsonString.parseJson.convertTo[createResponse].guidAssignments.convertTo[Map[String, String]].values.toList.head
            } else {
              jsonString.parseJson.convertTo[updateResponse].guidAssignments.convertTo[Map[String, String]].values.toList.head
            }
          }
        case response =>
          logger.debug(s"Atlas postData failed: ${response.status}")
          Future.failed(RestClientException(s"Atlas postData failed: ${response.status}"))
      }.recoverWith { case ex =>
        logger.debug(s"Atlas postData failed: ${ex.getMessage}")
        Future.failed(RestClientException(s"Atlas postData failed: ${ex.getMessage}", ex.getCause))
      }
  }

}

object RestClient {
  final case class RestClientException(private val message: String, private val cause: Throwable = None.orNull)
    extends Exception(message, cause)
}
