package com.ing.wbaa.airlock.proxy.provider.atlas

import spray.json._

object Model extends ModelJsonSupport {

  trait AtlasEntity

  // root atlas types
  trait Asset {
    def name: String
  }

  trait Referenceable {
    def qualifiedName: String
  }

  // classification of objects in atlas
  case class Classification(typeName: String)

  // dependency shortcut
  case class guidRef(guid: String, typeName: String)

  // Infra entity - server
  case class ServerAttributes(
      qualifiedName: String,
      name: String,
      server_name: String,
      ip_address: String)
    extends Asset with Referenceable

  case class Server(
      typeName: String,
      createdBy: String,
      attributes: ServerAttributes,
      classifications: Seq[Classification]) extends AtlasEntity

  // Infra entity - bucket
  case class BucketAttributes(
      qualifiedName: String,
      name: String)
    extends Asset with Referenceable

  case class Bucket(
      typeName: String,
      attributes: BucketAttributes,
      classifications: Seq[Classification]) extends AtlasEntity

  case class PseudoDirAttributes(
      qualifiedName: String,
      name: String,
      objectPrefix: String,
      bucket: guidRef) extends Asset with Referenceable

  case class PseudoDir(
      typeName: String,
      attributes: PseudoDirAttributes) extends AtlasEntity

  // Ingestion Process
  case class ClientProcessAttributes(
      qualifiedName: String,
      name: String,
      operation: String,
      run_as: String,
      server: guidRef,
      inputs: List[guidRef],
      outputs: List[guidRef]) extends Asset with Referenceable

  case class ClientProcess(
      typeName: String,
      createdBy: String,
      attributes: ClientProcessAttributes) extends AtlasEntity

  // Ingestion File
  case class BucketObjectAttributes(
      qualifiedName: String,
      name: String,
      dataType: String,
      pseudoDirectory: guidRef,
      classifications: Seq[Classification]) extends Asset with Referenceable

  case class BucketObject(
      typeName: String,
      attributes: BucketObjectAttributes) extends AtlasEntity

  // Entities Sequence
  case class Entities[A <: AtlasEntity](entities: Seq[A])

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

}
