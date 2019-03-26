package com.ing.wbaa.airlock.proxy.provider.atlas

import com.ing.wbaa.airlock.proxy.data.User
import spray.json.{ JsArray, JsNumber, JsObject, JsString }
import spray.json._

object ModelKafka extends DefaultJsonProtocol {

  private def rootMessage(message: JsObject) =
    JsObject(
      "version" -> JsObject("version" -> JsString("1.0.0")),
      "message" -> message
    )

  private def prepareEntities(userSTS: User, entities: JsArray) =
    JsObject(
      "entities" -> entities,
      "type" -> JsString("ENTITY_FULL_UPDATE"),
      "user" -> JsString(userSTS.userName.value)
    )

  private def referencedObject(name: String, typeName: String, guid: Long, entityState: String) =
    JsObject(
      "jsonClass" -> JsString("org.apache.atlas.typesystem.json.InstanceSerialization$_Id"),
      "id" -> JsString(s"-$guid"), // we do not care about GUID of entity
      "version" -> JsNumber(0),
      "typeName" -> JsString(typeName),
      "state" -> JsString(entityState)
    )

  private def deleteEntity(userSTS: User, name: String, typeName: String) =
    JsObject(
      "typeName" -> JsString(typeName),
      "attribute" -> JsString("qualifiedName"),
      "attributeValue" -> JsString(name),
      "type" -> JsString("ENTITY_DELETE"),
      "user" -> JsString(userSTS.userName.value)
    )

  private def baseEntityValues(name: String, owner: String, description: String = "Request via Airlock") =
    JsObject(
      "qualifiedName" -> JsString(name),
      "owner" -> JsString(owner),
      "description" -> JsString(description),
      "name" -> JsString(name)
    )

  def prepareEntity(userSTS: User, typeName: String, typeValues: JsObject, entityState: String, guid: Long) =
    JsObject(
      "jsonClass" -> JsString("org.apache.atlas.typesystem.json.InstanceSerialization$_Reference"),
      "id" -> JsObject(
        "jsonClass" -> JsString("org.apache.atlas.typesystem.json.InstanceSerialization$_Id"),
        "id" -> JsString(s"-$guid"), // we do not care about GUID of entity
        "version" -> JsNumber(0),
        "typeName" -> JsString(typeName),
        "state" -> JsString(entityState)
      ),
      "typeName" -> JsString(typeName),
      "values" -> typeValues,
      "traitNames" -> JsArray(), // not used at the moment by airlock
      "traits" -> JsObject() // not used at the moment by airlock
    )

  def bucketValues(name: String, userName: String): JsObject = baseEntityValues(name, userName)

  def psedudoDirValues(name: String, bucketName: String, bucketGuid: Long, userName: String, bucketType: String, entityState: String = "ACTIVE"): JsObject =
    JsObject(baseEntityValues(name, userName).fields ++
      Map(
        "objectPrefix" -> JsString(name),
        "bucket" -> referencedObject(bucketName, bucketType, bucketGuid, entityState)))

  def s3ObjectValues(name: String, pseudoDir: String, pseudoDirGuid: Long, userName: String, pseudoDirType: String, dataType: String, entityState: String = "ACTIVE"): JsObject =
    JsObject(baseEntityValues(name, userName).fields ++
      Map(
        "dataType" -> JsString(dataType),
        "pseudoDirectory" -> referencedObject(pseudoDir, pseudoDirType, pseudoDirGuid, entityState)))

  def serverValues(host: String, userName: String) =
    JsObject(baseEntityValues(host, userName).fields ++
      Map(
        "server_name" -> JsString(host),
        "ip_address" -> JsString(host)))

  def fsPathValues(name: String, userName: String, path: String) =
    JsObject(baseEntityValues(name, userName).fields ++
      Map(
        "path" -> JsString(path)))

  def processValues(name: String, userName: String, operation: String, host: String, hostType: String, serverGuid: Long,
      inName: String, inType: String, inGuid: Long, outName: String, outType: String, outGuid: Long, entityState: String = "ACTIVE") =
    JsObject(baseEntityValues(name, userName).fields ++
      Map(
        "operation" -> JsString(operation),
        "run_as" -> JsString(userName),
        "server" -> referencedObject(host, hostType, serverGuid, entityState),
        "inputs" -> JsArray(referencedObject(inName, inType, inGuid, entityState)),
        "outputs" -> JsArray(referencedObject(outName, outType, outGuid, entityState))
      )
    )

  def prepareEntityFullCreateMessage(userSTS: User, entityList: Vector[JsObject]): JsValue = {
    rootMessage(
      prepareEntities(userSTS, JsArray(entityList))
    ).toJson
  }

  def prepareEntityDeleteMessage(userSTS: User, name: String, typeName: String): JsValue =
    rootMessage(
      deleteEntity(userSTS, name, typeName)
    ).toJson
}
