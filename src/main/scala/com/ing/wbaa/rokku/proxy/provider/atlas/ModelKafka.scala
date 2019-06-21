package com.ing.wbaa.rokku.proxy.provider.atlas

import com.ing.wbaa.rokku.proxy.data.LineageLiterals._
import com.ing.wbaa.rokku.proxy.data.User
import spray.json.{ JsArray, JsNumber, JsObject, JsString, _ }

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

  private def baseEntityValues(name: String, owner: String, created: Long, description: String = "Request via Rokku") =
    JsObject(
      "qualifiedName" -> JsString(name),
      "owner" -> JsString(owner),
      "description" -> JsString(description),
      "name" -> JsString(name),
      "createTime" -> JsString(created.toString) // we always update timestamp when lineage changes
    )

  def prepareEntity(userName: String, typeName: String, typeValues: JsObject, entityState: String, guid: Long): JsObject =
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
      "traitNames" -> JsArray(), // not used at the moment by rokku
      "traits" -> JsObject() // not used at the moment by rokku
    )

  def prepareStrut(typeName: String, typeValues: JsObject): JsObject = {
    JsObject(
      "jsonClass" -> JsString("org.apache.atlas.typesystem.json.InstanceSerialization$_Struct"),
      "typeName" -> JsString(typeName),
      "values" -> typeValues
    )
  }

  def bucketEntity(name: String, userName: String, guid: Long, created: Long = System.currentTimeMillis()): JsObject =
    prepareEntity(userName, AWS_S3_BUCKET_TYPE,
      JsObject(baseEntityValues(name, userName, created).fields ++
        Map(
          "createtime" -> JsString(created.toString) // not a typo, this is actual filed name in Atlas 1.x/2.x
        )), ENTITY_ACTIVE, guid)

  def pseudoDirEntity(name: String, bucketName: String, bucketGuid: Long, userName: String, guid: Long,
      created: Long = System.currentTimeMillis(), entityState: String = "ACTIVE"): JsObject =
    prepareEntity(userName, AWS_S3_PSEUDO_DIR_TYPE,
      JsObject(baseEntityValues(name, userName, created).fields ++
        Map(
          "objectPrefix" -> JsString(name),
          "bucket" -> referencedObject(bucketName, AWS_S3_BUCKET_TYPE, bucketGuid, entityState))
      ), ENTITY_ACTIVE, guid)

  def s3ObjectEntity(name: String, pseudoDir: String, pseudoDirGuid: Long, userName: String, dataType: String, guid: Long,
      metadata: Option[Map[String, String]], created: Long = System.currentTimeMillis(), entityState: String = "ACTIVE"): JsObject =
    prepareEntity(userName, AWS_S3_OBJECT_TYPE,
      JsObject(baseEntityValues(name, userName, created).fields ++
        Map(
          "dataType" -> JsString(dataType),
          "pseudoDirectory" -> referencedObject(pseudoDir, AWS_S3_PSEUDO_DIR_TYPE, pseudoDirGuid, entityState),
          "awsTags" -> {
            JsArray(metadata.getOrElse(Map.empty).map {
              case (key, value) => prepareStrut(AWS_TAG, JsObject("key" -> JsString(key), "value" -> JsString(value)))
            }.toVector)
          })
      ), ENTITY_ACTIVE, guid)

  def serverEntity(host: String, userName: String, guid: Long, created: Long = System.currentTimeMillis()): JsObject =
    prepareEntity(userName, ROKKU_SERVER_TYPE,
      JsObject(baseEntityValues(host, userName, created).fields ++
        Map(
          "server_name" -> JsString(host),
          "ip_address" -> JsString(host))
      ), ENTITY_ACTIVE, guid)

  def fsPathEntity(name: String, userName: String, path: String, guid: Long, modified: Long = System.currentTimeMillis()): JsObject =
    prepareEntity(userName, HADOOP_FS_PATH,
      JsObject(baseEntityValues(name, userName, modified).fields ++
        Map(
          "path" -> JsString(path),
          "modifiedTime" -> JsString(modified.toString))
      ), ENTITY_ACTIVE, guid)

  def processEntity(name: String, userName: String, operation: String, host: String, serverGuid: Long,
      inName: String, inType: String, inGuid: Long, outName: String, outType: String, outGuid: Long, guid: Long,
      created: Long = System.currentTimeMillis(), entityState: String = "ACTIVE"): JsObject =
    prepareEntity(userName, ROKKU_CLIENT_TYPE,
      JsObject(baseEntityValues(name, userName, created).fields ++
        Map(
          "operation" -> JsString(operation),
          "run_as" -> JsString(userName),
          "server" -> referencedObject(host, ROKKU_SERVER_TYPE, serverGuid, entityState),
          "inputs" -> JsArray(referencedObject(inName, inType, inGuid, entityState)),
          "outputs" -> JsArray(referencedObject(outName, outType, outGuid, entityState))
        )
      ), ENTITY_ACTIVE, guid)

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
