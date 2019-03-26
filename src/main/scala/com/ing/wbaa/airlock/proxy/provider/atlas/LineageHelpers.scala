package com.ing.wbaa.airlock.proxy.provider.atlas

import akka.Done
import akka.http.scaladsl.model.{ HttpRequest, RemoteAddress }
import com.ing.wbaa.airlock.proxy.data._
import com.ing.wbaa.airlock.proxy.handler.LoggerHandlerWithId
import com.ing.wbaa.airlock.proxy.provider.atlas.ModelKafka._
import com.ing.wbaa.airlock.proxy.provider.kafka.EventProducer
import spray.json.JsObject

import scala.concurrent.Future

trait LineageHelpers extends EventProducer {

  private val logger = new LoggerHandlerWithId

  val AWS_S3_OBJECT_TYPE = "aws_s3_object"
  val AWS_S3_BUCKET_TYPE = "aws_s3_bucket"
  val AWS_S3_PSEUDO_DIR_TYPE = "aws_s3_pseudo_dir"
  val HADOOP_FS_PATH = "fs_path"
  val AIRLOCK_CLIENT_TYPE = "airlock_client"
  val AIRLOCK_SERVER_TYPE = "server"
  val EXTERNAL_OBJECT_IN = "external_object_in"
  val EXTERNAL_OBJECT_OUT = "external_object_out"
  val ATLAS_HOOK_TOPIC = "ATLAS_HOOK"
  val ENTITY_ACTIVE = "ACTIVE"

  private def timestamp: Long = System.currentTimeMillis()

  private def newGuid = System.nanoTime()

  private def extractClient(userAgent: String): Option[String] =
    """(\S+)/\S+""".r
      .findFirstMatchIn(userAgent)
      .map(_ group 1)

  private def extractHeaderOption(httpRequest: HttpRequest, header: String): Option[String] =
    if (httpRequest.getHeader(header).isPresent)
      Some(httpRequest.getHeader(header).get().value())
    else
      None

  def getLineageHeaders(httpRequest: HttpRequest): LineageHeaders = {
    val path = httpRequest.uri.path.toString().split("/").filter(_.nonEmpty)
    val pseudoDir =
      if (path.length > 2)
        Some(path.dropRight(1).mkString("/"))
      else
        None
    val bucketObjectFQN = if (path.length > 1) Some(path.mkString("/")) else None

    LineageHeaders(
      extractHeaderOption(httpRequest, "Remote-Address"),
      path.head,
      pseudoDir,
      bucketObjectFQN,
      httpRequest.method,
      httpRequest.entity.contentType,
      extractHeaderOption(httpRequest, "User-Agent").flatMap(extractClient),
      httpRequest.uri.rawQueryString,
      extractHeaderOption(httpRequest, "x-amz-copy-source")
    )
  }

  // file/object entity delete
  // for now it is just deleting file entity and no related objects like eg. aws_cli_script, which uploaded or downloaded
  // file to bucket. Once we delete aws_cli_script object we will lose track of whats has been deleted
  // We need to come up with process of tracking file delete
  def deleteEntityLineage(lh: LineageHeaders, userSTS: User, entityType: String = AWS_S3_OBJECT_TYPE)(implicit id: RequestId): Future[Done] = {
    logger.debug(s"Creating Delete lineage for request to ${lh.method} file ${lh.bucketObject} at ${lh.bucket} at $timestamp")
    sendSingleMessage(prepareEntityDeleteMessage(userSTS, lh.bucketObject.getOrElse(""), entityType).toString(), ATLAS_HOOK_TOPIC)
  }

  private def generateGuids = LineageObjectGuids(newGuid, newGuid, newGuid, newGuid, newGuid, newGuid)

  //todo: upload process and direction read/write etc
  def kafkaReadOrWriteLineage(lh: LineageHeaders, userSTS: User, method: AccessType, clientIPAddress: RemoteAddress, externalFsPath: Option[String] = None, guids: LineageObjectGuids = generateGuids)(implicit id: RequestId): Future[Done] = {
    val userName = userSTS.userName.value
    val clientHost = clientIPAddress.getAddress().get().getHostAddress
    val clientType = lh.clientType.getOrElse("generic")
    val bucketObject = lh.bucketObject.getOrElse("emptyObject")
    val pseudoDir = lh.pseduoDir.getOrElse(s"${lh.bucket}/")
    val externalPath = externalFsPath.getOrElse("")

    // entity definitions
    val serverEntity = prepareEntity(userSTS, AIRLOCK_SERVER_TYPE, serverValues(clientHost, userName), ENTITY_ACTIVE, guids.serverGuid)
    val bucketEntity = prepareEntity(userSTS, AWS_S3_BUCKET_TYPE, bucketValues(lh.bucket, userName), ENTITY_ACTIVE, guids.bucketGuid)
    val pseudoDirEntity = prepareEntity(userSTS, AWS_S3_PSEUDO_DIR_TYPE,
      psedudoDirValues(pseudoDir, lh.bucket, guids.bucketGuid, userName, AWS_S3_BUCKET_TYPE), ENTITY_ACTIVE, guids.pseudoDir)
    val s3ObjectEntity = prepareEntity(userSTS, AWS_S3_OBJECT_TYPE,
      s3ObjectValues(bucketObject, pseudoDir, guids.pseudoDir, userName, AWS_S3_PSEUDO_DIR_TYPE, lh.contentType.toString()), ENTITY_ACTIVE, guids.objectGuid)
    val externalPathEntity = prepareEntity(userSTS, HADOOP_FS_PATH,
      fsPathValues(externalPath, userName, externalPath), ENTITY_ACTIVE, guids.externalPathGuid)
    val userProcessEntity: JsObject => JsObject = procProps => prepareEntity(userSTS, AIRLOCK_CLIENT_TYPE, procProps, ENTITY_ACTIVE, System.nanoTime())

    method match {
      case Read =>
        logger.debug(s"Creating $method lineage for request for file $bucketObject at $lh.bucket at $timestamp")
        println("readProces ------ : " + userProcessEntity(processValues(s"${clientType}_$timestamp", userName, method.rangerName,
          clientHost, AIRLOCK_SERVER_TYPE, guids.serverGuid,
          bucketObject, AWS_S3_OBJECT_TYPE, guids.objectGuid,
          externalPath, HADOOP_FS_PATH, guids.externalPathGuid)))

        sendSingleMessage(
          prepareEntityFullCreateMessage(userSTS, Vector(serverEntity, bucketEntity, pseudoDirEntity, s3ObjectEntity, externalPathEntity,
            userProcessEntity(processValues(s"${clientType}_$timestamp", userName, method.rangerName,
              clientHost, AIRLOCK_SERVER_TYPE, guids.serverGuid,
              bucketObject, AWS_S3_OBJECT_TYPE, guids.objectGuid,
              externalPath, HADOOP_FS_PATH, guids.externalPathGuid))))
            .toString, ATLAS_HOOK_TOPIC)

      case Write if externalPath.length > 0 =>
        logger.debug(s"Creating $method lineage for request for file $bucketObject at $lh.bucket at $timestamp")

        println("writeProcess -------: " + userProcessEntity(processValues(s"${clientType}_$timestamp", userName, method.rangerName,
          clientHost, AIRLOCK_SERVER_TYPE, guids.serverGuid,
          externalPath, HADOOP_FS_PATH, guids.externalPathGuid,
          pseudoDir, AWS_S3_PSEUDO_DIR_TYPE, guids.pseudoDir)))

        sendSingleMessage(
          prepareEntityFullCreateMessage(userSTS, Vector(serverEntity, bucketEntity, pseudoDirEntity, s3ObjectEntity, externalPathEntity,
            userProcessEntity(processValues(s"${clientType}_$timestamp", userName, method.rangerName,
              clientHost, AIRLOCK_SERVER_TYPE, guids.serverGuid,
              externalPath, HADOOP_FS_PATH, guids.externalPathGuid,
              pseudoDir, AWS_S3_PSEUDO_DIR_TYPE, guids.pseudoDir))))
            .toString, ATLAS_HOOK_TOPIC)

      case Write => // todo: check if used?
        logger.debug(s"Creating $method lineage for request for file $bucketObject at $lh.bucket at $timestamp")
        sendSingleMessage(
          prepareEntityFullCreateMessage(userSTS, Vector(serverEntity, bucketEntity, pseudoDirEntity, s3ObjectEntity, externalPathEntity,
            userProcessEntity(processValues(s"${clientType}_$timestamp", userName, method.rangerName,
              clientHost, AIRLOCK_SERVER_TYPE, guids.serverGuid,
              bucketObject, AWS_S3_OBJECT_TYPE, guids.objectGuid,
              pseudoDir, AWS_S3_PSEUDO_DIR_TYPE, guids.pseudoDir))))
            .toString, ATLAS_HOOK_TOPIC)

      case _ => Future(Done)
    }
  }

  def lineageForCopyOperation(lh: LineageHeaders, userSTS: User, method: AccessType, clientIPAddress: RemoteAddress, srcGuids: LineageObjectGuids = generateGuids)(implicit id: RequestId): Future[Done] = {
    val userName = userSTS.userName.value
    val clientHost = clientIPAddress.getAddress().get().getHostAddress
    val clientType = lh.clientType.getOrElse("generic")
    val bucketObject = lh.bucketObject.getOrElse("emptyObject")
    val pseudoDir = lh.pseduoDir.getOrElse(s"${lh.bucket}/")
    val objectNameFromCopySrc = lh.copySource.get
    val bucketNameFromCopySrc = lh.copySource.get.split("/").head
    val pseudoDirFromCopySrc = {
      val pathArray = lh.copySource.get.split("/").dropRight(1)
      if (pathArray.length >= 2)
        pathArray.mkString("/")
      else
        pathArray.mkString + "/"
    }
    val destGuids = generateGuids
    val serverEntity = prepareEntity(userSTS, AIRLOCK_SERVER_TYPE, serverValues(clientHost, userName), ENTITY_ACTIVE, srcGuids.serverGuid)

    val srcBucketEntity = prepareEntity(userSTS, AWS_S3_BUCKET_TYPE, bucketValues(bucketNameFromCopySrc, userName), ENTITY_ACTIVE, srcGuids.bucketGuid)
    val srcPseudoDirEntity = prepareEntity(userSTS, AWS_S3_PSEUDO_DIR_TYPE,
      psedudoDirValues(pseudoDirFromCopySrc, bucketNameFromCopySrc, srcGuids.bucketGuid, userName, AWS_S3_BUCKET_TYPE), ENTITY_ACTIVE, srcGuids.pseudoDir)
    val srcS3ObjectEntity = prepareEntity(userSTS, AWS_S3_OBJECT_TYPE,
      s3ObjectValues(objectNameFromCopySrc, pseudoDirFromCopySrc, srcGuids.pseudoDir, userName, AWS_S3_PSEUDO_DIR_TYPE, lh.contentType.toString()), ENTITY_ACTIVE, srcGuids.objectGuid)

    val destBucketEntity = prepareEntity(userSTS, AWS_S3_BUCKET_TYPE, bucketValues(lh.bucket, userName), ENTITY_ACTIVE, destGuids.bucketGuid)
    val destPseudoDirEntity =
      if (bucketNameFromCopySrc == lh.bucket) {
        prepareEntity(userSTS, AWS_S3_PSEUDO_DIR_TYPE,
          psedudoDirValues(pseudoDir, bucketNameFromCopySrc, srcGuids.bucketGuid, userName, AWS_S3_BUCKET_TYPE), ENTITY_ACTIVE, destGuids.pseudoDir)
      } else {
        prepareEntity(userSTS, AWS_S3_PSEUDO_DIR_TYPE,
          psedudoDirValues(pseudoDir, lh.bucket, destGuids.bucketGuid, userName, AWS_S3_BUCKET_TYPE), ENTITY_ACTIVE, destGuids.pseudoDir)
      }
    val destS3ObjectEntity = prepareEntity(userSTS, AWS_S3_OBJECT_TYPE,
      s3ObjectValues(bucketObject, pseudoDir, destGuids.pseudoDir, userName, AWS_S3_PSEUDO_DIR_TYPE, lh.contentType.toString()), ENTITY_ACTIVE, destGuids.objectGuid)

    val copyProcessEntity = prepareEntity(userSTS, AIRLOCK_CLIENT_TYPE,
      processValues(s"${clientType}_$timestamp", userName, method.rangerName, clientHost, AIRLOCK_SERVER_TYPE, srcGuids.serverGuid,
        objectNameFromCopySrc, AWS_S3_OBJECT_TYPE, srcGuids.objectGuid,
        bucketObject, AWS_S3_OBJECT_TYPE, destGuids.objectGuid), ENTITY_ACTIVE, destGuids.processGuid)

    if (bucketNameFromCopySrc == lh.bucket) {
      sendSingleMessage(
        prepareEntityFullCreateMessage(
          userSTS,
          Vector(serverEntity, srcBucketEntity, srcPseudoDirEntity, srcS3ObjectEntity, destPseudoDirEntity, destS3ObjectEntity, copyProcessEntity)).toString(),
        ATLAS_HOOK_TOPIC)
    } else {
      sendSingleMessage(
        prepareEntityFullCreateMessage(
          userSTS,
          Vector(serverEntity, srcBucketEntity, srcPseudoDirEntity, srcS3ObjectEntity, destBucketEntity, destPseudoDirEntity, destS3ObjectEntity, copyProcessEntity)).toString(),
        ATLAS_HOOK_TOPIC)
    }
  }
}
