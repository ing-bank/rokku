package com.ing.wbaa.rokku.proxy.provider.atlas

import com.ing.wbaa.rokku.proxy.data.LineageLiterals._
import com.ing.wbaa.rokku.proxy.data.User
import spray.json.{ JsArray, JsNumber, JsObject, JsString, _ }

object ModelKafka extends DefaultJsonProtocol {

  private def rootMessage(message: JsObject, userSTS: User) =
    JsObject(
      "entities" -> message,
      "type" -> JsString("ENTITY_FULL_UPDATE_V2"),
      "user" -> JsString(userSTS.userName.value)
    )

  private def prepareEntities(entities: JsArray) =
    JsObject(
      "entities" -> entities
    )

  private def referencedObject(name: String, typeName: String, guid: Long, entityState: String) =
    JsObject(
      "guid" -> JsString(s"-$guid"), // we do not care about GUID of entity
      "version" -> JsNumber(0),
      "typeName" -> JsString(typeName),
      "state" -> JsString(entityState)
    )

  private def deleteEntity(userSTS: User, name: String, typeName: String) =
    JsObject(
      "typeName" -> JsString(typeName),
      "attribute" -> JsString("qualifiedName"),
      "attributeValue" -> JsString(name),
      "type" -> JsString("ENTITY_DELETE_V2"),
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

  def prepareEntity(userName: String, typeName: String, typeValues: JsObject, entityState: String, guid: Long, classifications: JsArray): Option[JsObject] =
    Some(JsObject(
      "guid" -> JsString(s"-$guid"),
      "typeName" -> JsString(typeName),
      "attributes" -> typeValues,
      "classifications" -> classifications
    ))

  def prepareStrut(typeName: String, typeValues: JsObject): JsObject = {
    JsObject(
      "typeName" -> JsString(typeName),
      "attributes" -> typeValues
    )
  }

  def bucketEntity(name: String, userName: String, guid: Long, classifications: Seq[String], created: Long = System.currentTimeMillis()): Option[JsObject] =
    prepareEntity(userName, AWS_S3_BUCKET_TYPE,
      JsObject(baseEntityValues(name, userName, created).fields ++
        Map(
          "createtime" -> JsString(created.toString) // not a typo, this is actual filed name in Atlas 1.x/2.x
        )), ENTITY_ACTIVE, guid, JsArray(classifications.map(classification => JsObject("typeName" -> JsString(classification))).toVector))

  def pseudoDirEntity(name: String, bucketName: String, bucketGuid: Long, userName: String, guid: Long, classifications: Seq[String],
      created: Long = System.currentTimeMillis(), entityState: String = "ACTIVE"): Option[JsObject] =
    prepareEntity(userName, AWS_S3_PSEUDO_DIR_TYPE,
      JsObject(baseEntityValues(name, userName, created).fields ++
        Map(
          "objectPrefix" -> JsString(name),
          "bucket" -> referencedObject(bucketName, AWS_S3_BUCKET_TYPE, bucketGuid, entityState))
      ), ENTITY_ACTIVE, guid, JsArray(classifications.map(classification => JsObject("typeName" -> JsString(classification))).toVector))

  def s3ObjectEntity(objName: Option[String], pseudoDir: String, pseudoDirGuid: Long, userName: String, dataType: String, guid: Long,
      metadata: Option[Map[String, String]], classifications: Seq[String], created: Long = System.currentTimeMillis(), entityState: String = "ACTIVE"): Option[JsObject] =
    objName.flatMap(name => prepareEntity(userName, AWS_S3_OBJECT_TYPE,
      JsObject(baseEntityValues(name, userName, created).fields ++
        Map(
          "dataType" -> JsString(dataType),
          "pseudoDirectory" -> referencedObject(pseudoDir, AWS_S3_PSEUDO_DIR_TYPE, pseudoDirGuid, entityState),
          "awsTags" -> {
            JsArray(metadata.getOrElse(Map.empty).map {
              case (key, value) => prepareStrut(AWS_TAG, JsObject("key" -> JsString(key), "value" -> JsString(value)))
            }.toVector)
          })
      ), ENTITY_ACTIVE, guid, JsArray(classifications.map(classification => JsObject("typeName" -> JsString(classification))).toVector)))

  def serverEntity(host: String, userName: String, guid: Long, created: Long = System.currentTimeMillis()): Option[JsObject] =
    prepareEntity(userName, ROKKU_SERVER_TYPE,
      JsObject(baseEntityValues(host, userName, created).fields ++
        Map(
          "server_name" -> JsString(host),
          "ip_address" -> JsString(host))
      ), ENTITY_ACTIVE, guid, JsArray())

  def fsPathEntity(name: String, userName: String, path: String, guid: Long, modified: Long = System.currentTimeMillis()): Option[JsObject] =
    prepareEntity(userName, HADOOP_FS_PATH,
      JsObject(baseEntityValues(name, userName, modified).fields ++
        Map(
          "path" -> JsString(path),
          "modifiedTime" -> JsString(modified.toString))
      ), ENTITY_ACTIVE, guid, JsArray())

  def processEntity(name: String, userName: String, operation: String, host: String, serverGuid: Long,
      inName: String, inType: String, inGuid: Long, outName: String, outType: String, outGuid: Long, guid: Long,
      created: Long = System.currentTimeMillis(), entityState: String = "ACTIVE"): Option[JsObject] =
    prepareEntity(userName, ROKKU_CLIENT_TYPE,
      JsObject(baseEntityValues(name, userName, created).fields ++
        Map(
          "operation" -> JsString(operation),
          "run_as" -> JsString(userName),
          "server" -> referencedObject(host, ROKKU_SERVER_TYPE, serverGuid, entityState),
          "inputs" -> JsArray(referencedObject(inName, inType, inGuid, entityState)),
          "outputs" -> JsArray(referencedObject(outName, outType, outGuid, entityState))
        )
      ), ENTITY_ACTIVE, guid, JsArray())

  def prepareEntityFullCreateMessage(userSTS: User, entityList: Vector[Option[JsObject]]): JsValue = {
    val entities = entityList.filter(_.isDefined).flatten
    rootMessage(
      prepareEntities(JsArray(entities)), userSTS
    ).toJson
  }

  def prepareEntityDeleteMessage(userSTS: User, name: String, typeName: String): JsValue =
    rootMessage(
      deleteEntity(userSTS, name, typeName), userSTS
    ).toJson
}
