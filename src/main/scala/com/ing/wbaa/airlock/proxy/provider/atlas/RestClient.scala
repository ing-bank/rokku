package com.ing.wbaa.airlock.proxy.provider.atlas

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{ Authorization, BasicHttpCredentials }
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import com.ing.wbaa.airlock.proxy.config.AtlasSettings
import com.ing.wbaa.airlock.proxy.provider.atlas.Model.{ CreateResponse, EntityId, EntitySearchResult, UpdateResponse }
import com.ing.wbaa.airlock.proxy.provider.atlas.RestClient.RestClientException
import com.typesafe.scalalogging.LazyLogging
import spray.json.{ JsValue, _ }

import scala.concurrent.{ ExecutionContext, Future }

trait RestClient extends AtlasModelJsonSupport with LazyLogging {

  protected[this] implicit def system: ActorSystem
  protected[this] implicit def executionContext: ExecutionContext
  protected[this] implicit def materializer: Materializer

  protected[this] implicit def atlasSettings: AtlasSettings

  private lazy val http = Http(system)
  private lazy val atlasApiUriV1 = atlasSettings.atlasBaseUri.withPath(Uri.Path("/api/atlas"))
  private lazy val atlasApiUriV2 = atlasSettings.atlasBaseUri.withPath(Uri.Path("/api/atlas/v2"))
  private lazy val bulkEntity = "/entity/bulk"
  private lazy val entityGuid = "/entity/guid"

  private lazy val authHeader = Authorization(BasicHttpCredentials(atlasSettings.atlasApiUser, atlasSettings.atlasApiPassword))

  /**
   * Search entity Guid for given name
   *
   * @param typeName
   * @param value
   * @return Guid of entity
   */
  def getEntityGUID(typeName: String, value: String): Future[String] = {
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

  /**
   * Delete entity by GUID
   *
   * @param guid
   * @return Guid of deleted entity
   */
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

  /**
   * Creates or updates entity
   * Post data will either create or update entity (Atlas API behaviour)
   *
   * @param json
   * @return Guid of changed or added entity
   */
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
              jsonString.parseJson.convertTo[CreateResponse].guidAssignments.convertTo[Map[String, String]].values.toList.head
            } else {
              jsonString.parseJson.convertTo[UpdateResponse].guidAssignments.convertTo[Map[String, String]].values.toList.head
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
