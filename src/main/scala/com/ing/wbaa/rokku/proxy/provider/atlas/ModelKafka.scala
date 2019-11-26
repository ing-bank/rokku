package com.ing.wbaa.rokku.proxy.provider.atlas

import com.ing.wbaa.rokku.proxy.data.LineageLiterals._
import com.ing.wbaa.rokku.proxy.data.User
import spray.json.{ JsArray, JsNumber, JsObject, JsString, _ }

object ModelKafka extends DefaultJsonProtocol {

  private def rootMessage(message: JsObject, userSTS: User, typeName: String) =
    JsObject(
      "entities" -> message,
      "type" -> JsString(typeName),
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
      "type" -> JsString("ENTITY_DELETE"),
      "user" -> JsString(userSTS.userName.value)
    )

  private def baseEntityValues(name: String, qualifiedName: String, owner: String, created: Long, addCreateTime: Boolean = true, description: String = "Request via Rokku") = {
    val jObj = JsObject(
      "qualifiedName" -> JsString(qualifiedName),
      "owner" -> JsString(owner),
      "description" -> JsString(description),
      "name" -> JsString(name)
    )

    if (addCreateTime) {
      JsObject(jObj.fields ++ Map("createTime" -> JsString(created.toString)))
    } else {
      jObj
    }
  }

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

  def bucketEntity(name: String, userName: String, guid: Long, classifications: Seq[String], created: Long = System.currentTimeMillis()): Option[JsObject] = {
    val bucketNameWithPrefix = s"s3://$name"
    prepareEntity(userName, AWS_S3_BUCKET_TYPE,
      JsObject(baseEntityValues(name, bucketNameWithPrefix, userName, created, addCreateTime = false).fields ++
        Map(
          "createtime" -> JsString(created.toString) // not a typo, this is actual filed name in Atlas 1.x/2.x
        )), ENTITY_ACTIVE, guid, JsArray(classifications.map(classification => JsObject("typeName" -> JsString(classification))).toVector))
  }

  def pseudoDirEntity(name: String, bucketName: String, bucketGuid: Long, userName: String, guid: Long, classifications: Seq[String],
      created: Long = System.currentTimeMillis(), entityState: String = "ACTIVE"): Option[JsObject] = {
    val dirWithoutBucketName = name.split("/").drop(1).mkString("/")
    val dirName = if (dirWithoutBucketName.isEmpty) "/" else s"/$dirWithoutBucketName/"
    val dirNameWithPrefix = s"s3://$name"
    prepareEntity(userName, AWS_S3_PSEUDO_DIR_TYPE,
      JsObject(baseEntityValues(dirName, dirNameWithPrefix, userName, created).fields ++
        Map(
          "objectPrefix" -> JsString(dirNameWithPrefix),
          "bucket" -> referencedObject(bucketName, AWS_S3_BUCKET_TYPE, bucketGuid, entityState))
      ), ENTITY_ACTIVE, guid, JsArray(classifications.map(classification => JsObject("typeName" -> JsString(classification))).toVector))
  }

  def s3ObjectEntity(objName: Option[String], pseudoDir: String, pseudoDirGuid: Long, userName: String, dataType: String, guid: Long,
      metadata: Option[Map[String, String]], classifications: Seq[String], created: Long = System.currentTimeMillis(), entityState: String = "ACTIVE"): Option[JsObject] =
    objName.flatMap(name => prepareEntity(userName, AWS_S3_OBJECT_TYPE,
      JsObject(baseEntityValues(name.split("/").last.mkString(""), name, userName, created).fields ++
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
      JsObject(baseEntityValues(host, host, userName, created, addCreateTime = false).fields ++
        Map(
          "server_name" -> JsString(host),
          "ip_address" -> JsString(host))
      ), ENTITY_ACTIVE, guid, JsArray())

  def fsPathEntity(name: String, userName: String, path: String, guid: Long, modified: Long = System.currentTimeMillis()): Option[JsObject] =
    prepareEntity(userName, HADOOP_FS_PATH,
      JsObject(baseEntityValues(name, name, userName, modified).fields ++
        Map(
          "path" -> JsString(path),
          "modifiedTime" -> JsString(modified.toString))
      ), ENTITY_ACTIVE, guid, JsArray())

  def processEntity(name: String, userName: String, operation: String, host: String, serverGuid: Long,
      inName: String, inType: String, inGuid: Long, outName: String, outType: String, outGuid: Long, guid: Long,
      created: Long = System.currentTimeMillis(), entityState: String = "ACTIVE"): Option[JsObject] =
    prepareEntity(userName, ROKKU_CLIENT_TYPE,
      JsObject(baseEntityValues(name, name, userName, created, addCreateTime = false).fields ++
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
      prepareEntities(JsArray(entities)), userSTS, "ENTITY_FULL_UPDATE_V2"
    ).toJson
  }

  def prepareEntityDeleteMessage(userSTS: User, name: String, typeName: String): JsValue = {
    JsObject(
      "version" -> JsObject("version" -> JsString("1.0.0")),
      "message" -> deleteEntity(userSTS, name, typeName)
    ).toJson
  }
}

