package com.ing.wbaa.airlock.proxy.provider.atlas

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._

object Model extends AtlasModelJsonSupport {

  // root atlas types
  trait Asset {
    def name: String
  }

  trait Referenceable {
    def qualifiedName: String
  }

  // classification of objects in atlas
  case class Classification(typeName: String)

  // Infra entity - server
  case class ServerAttributes(qualifiedName: String, name: String, server_name: String, ip_address: String)
    extends Asset with Referenceable

  // dependency shortcut
  case class guidRef(guid: String, typeName: String)

  // input objects
  trait Inputs {
    def inputs: List[guidRef]
  }

  // output objects
  trait Outputs {
    def outputs: List[guidRef]
  }

  case class Server(
      typeName: String,
      createdBy: String,
      attributes: ServerAttributes,
      classifications: Seq[Classification])

  // Infra entity - bucket
  case class BucketAttributes(
      qualifiedName: String,
      name: String,
      bucket_name: String)
    extends Asset with Referenceable

  case class Bucket(
      typeName: String,
      createdBy: String,
      attributes: BucketAttributes,
      classifications: Seq[Classification])

  // Ingestion Process
  case class IngestionAttributes(
      qualifiedName: String,
      name: String,
      operation: String,
      run_as: String,
      Server: guidRef,
      inputs: List[guidRef],
      outputs: List[guidRef])
    extends Asset with Referenceable with Inputs with Outputs

  case class Ingestion(
      typeName: String,
      createdBy: String,
      attributes: IngestionAttributes
  )

  // Ingestion File
  case class FileAttributes(
      qualifiedName: String,
      name: String,
      file_name: String,
      format: String,
      bucket: guidRef,
      Server: guidRef,
      inputs: List[guidRef],
      outputs: List[guidRef],
      classifications: Seq[Classification])
    extends Asset with Referenceable with Inputs with Outputs

  case class IngestedFile(
      typeName: String,
      createdBy: String,
      attributes: FileAttributes
  )

  // Entities Sequence
  case class Entities[A](entities: Seq[A])

  // Entity search result
  case class EntityId(
      state: String,
      jsonClass: String,
      typeName: String,
      version: Int,
      id: String)

  case class Definition(
      typeName: String,
      values: JsObject,
      id: EntityId,
      traits: JsObject,
      traitNames: JsObject,
      systemAttributes: JsObject, jsonClass: String)

  case class EntitySearchResult(
      requestId: String,
      definition: JsObject)

  // Entity create / update result
  trait GuidResponse {
    def guidAssignments: JsObject
  }

  case class CreateResponse(
      mutatedEntities: JsObject,
      guidAssignments: JsObject) extends GuidResponse

  case class UpdateResponse(guidAssignments: JsObject) extends GuidResponse

  // lineage response
  // below four classes (LineageRelations, LineageResponse, Relationship, RelationshipResponse) are not used at the moment.
  // they are to be used, once we start working with entities lineage
  case class LineageRelations(
      fromEntityId: String,
      toEntityId: String,
      relationshipId: String)

  case class LineageResponse(
      baseEntityGuid: String,
      lineageDirection: String,
      lineageDepth: Int,
      guidEntityMap: JsObject,
      relations: Seq[LineageRelations])

  case class Relationship(
      typeName: String,
      guid: String,
      end1: JsObject,
      end2: JsObject,
      label: String,
      propagateTags: String,
      status: String,
      createdBy: String,
      updatedBy: String,
      createTime: Long,
      updateTime: Long,
      version: Int,
      propagatedClassifications: JsObject,
      blockedPropagatedClassifications: JsObject)

  case class RelationshipResponse(relationship: Relationship)

}

trait AtlasModelJsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  import Model._

  //generic
  implicit val readsClassification = jsonFormat1(Classification)
  implicit val readsGuidRef = jsonFormat2(guidRef)
  //server
  implicit val readsServerAttributes = jsonFormat4(ServerAttributes)
  implicit val readsServer = jsonFormat4(Server)
  implicit val readsServerEntities = jsonFormat1(Entities[Server])
  //bucket
  implicit val readsBucketAttributes = jsonFormat3(BucketAttributes)
  implicit val readsBucket = jsonFormat4(Bucket)
  implicit val readsBucketEntities = jsonFormat1(Entities[Bucket])
  // Entity search result
  implicit val idReader = jsonFormat5(EntityId)
  implicit val readsDefinition = jsonFormat7(Definition)
  implicit val resultReader = jsonFormat2(EntitySearchResult)
  // Entity create / update result
  implicit val readsCreateResponse = jsonFormat2(CreateResponse)
  implicit val readsUpdateResponse = jsonFormat1(UpdateResponse)
  // IngestionProcess
  implicit val readsIngestionAttributes = jsonFormat7(IngestionAttributes)
  implicit val readsIngestion = jsonFormat3(Ingestion)
  implicit val readsIngestionEntities = jsonFormat1(Entities[Ingestion])
  // File
  implicit val readsFileAttributes = jsonFormat9(FileAttributes)
  implicit val readsFile = jsonFormat3(IngestedFile)
  implicit val readsFileEntities = jsonFormat1(Entities[IngestedFile])

}
