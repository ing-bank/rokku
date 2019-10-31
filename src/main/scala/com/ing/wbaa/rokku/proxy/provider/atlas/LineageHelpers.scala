package com.ing.wbaa.rokku.proxy.provider.atlas

import akka.Done
import akka.http.scaladsl.model.HttpRequest
import com.ing.wbaa.rokku.proxy.data.LineageLiterals._
import com.ing.wbaa.rokku.proxy.data._
import com.ing.wbaa.rokku.proxy.handler.LoggerHandlerWithId
import com.ing.wbaa.rokku.proxy.provider.atlas.ModelKafka._
import com.ing.wbaa.rokku.proxy.provider.kafka.EventProducer
import com.ing.wbaa.rokku.proxy.util.S3Utils
import spray.json.JsObject

import scala.concurrent.Future
import scala.util.{ Failure, Success, Try }

trait LineageHelpers extends EventProducer {

  private val logger = new LoggerHandlerWithId

  private def timestamp: Long = System.currentTimeMillis()

  private def extractClient(userAgent: String): Option[String] =
    """(\S+)/\S+""".r
      .findFirstMatchIn(userAgent)
      .map(_ group 1)

  val CLASSIFICATIONS_HEADER = "rokku-classifications"
  val METADATA_HEADER = "rokku-metadata"

  private def extractHeaderOption(httpRequest: HttpRequest, header: String): Option[String] =
    if (httpRequest.getHeader(header).isPresent)
      Some(httpRequest.getHeader(header).get().value())
    else
      None

  def extractMetadataHeader(metadata: Option[String])(implicit id: RequestId): Option[Map[String, String]] = {
    if (metadata.isDefined) {
      Try(metadata.get.split(",").map(_.split("=")).map(arr => arr(0) -> arr(1)).toMap) match {
        case Success(value) => Some(value)
        case Failure(exception) =>
          logger.warn("cannot parse rokku metadata header ex=", exception)
          None
      }
    } else {
      None
    }
  }

  def getLineageHeaders(httpRequest: HttpRequest)(implicit id: RequestId): LineageHeaders = {
    val fullPath = S3Utils.getPathName(httpRequest)
    val bucketName = getBucketName(fullPath)
    val pseudoDir = getPathDir(fullPath)
    val bucketObjectFQN = getObjectName(fullPath)

    logger.debug("lineage header fullPath={}, pseudoDir={}, bucketObject={}", fullPath, pseudoDir, bucketObjectFQN)

    LineageHeaders(
      extractHeaderOption(httpRequest, "Remote-Address"),
      bucketName,
      pseudoDir,
      bucketObjectFQN,
      httpRequest.method,
      httpRequest.entity.contentType,
      extractHeaderOption(httpRequest, "User-Agent").flatMap(extractClient),
      httpRequest.uri.rawQueryString,
      extractHeaderOption(httpRequest, "x-amz-copy-source"),
      extractClassifications(httpRequest),
      extractMetadataHeader(extractHeaderOption(httpRequest, METADATA_HEADER))
    )
  }

  def getBucketName(fullPath: String): String = fullPath.split("/").filter(_.nonEmpty).head

  def getObjectName(fullPath: String): Option[String] = {
    val path = fullPath.split("/").filter(_.nonEmpty)
    if (path.length > 1 && !fullPath.endsWith("/")) Some(s"""s3://${path.mkString("/")}""") else None
  }

  def getPathDir(fullPath: String): Option[String] = {
    val path = fullPath.split("/").filter(_.nonEmpty)
    if (isOnlyBucketName(path))
      if (!fullPath.endsWith("/"))
        Some(s"${path.dropRight(1).mkString("/")}/")
      else if (fullPath.startsWith("/"))
        Some(fullPath.drop(1))
      else
        Some(fullPath)
    else
      None
  }

  private def isOnlyBucketName(path: Array[String]): Boolean = {
    path.length > 1
  }

  /**
   * get classifications from http header and set on right entity in atlas
   * - the path is made from one element the classification is set on bucket
   * - the path is made from more than one element and end with "/" - classification is set on pseudoDir
   * - the path is made from more than one element - classification is set on s3_object
   *
   * @param httpRequest - the request to get header
   * @param id          - session id
   * @return map with entity name and list of classifications
   */
  def extractClassifications(httpRequest: HttpRequest)(implicit id: RequestId): Map[ClassificationFor, Seq[String]] = {
    val path = httpRequest.uri.path.toString()
    val classificationFor: ClassificationFor = path.split("/").filter(_.nonEmpty) match {
      case Array(_)                        => BucketClassification()
      case Array(_*) if path.endsWith("/") => DirClassification()
      case Array(_*)                       => ObjectClassification()
      case _ =>
        logger.warn("unknown classification for {}", path)
        UnknownClassification()
    }
    Map(classificationFor -> extractHeaderOption(httpRequest, CLASSIFICATIONS_HEADER).map(_.split(",").toList).getOrElse(Seq.empty))
  }

  // file/object/bucket object entity delete
  // for now it is just deleting file entity and no related objects like eg. aws_cli_script, which uploaded or downloaded
  // file to bucket. Once we delete aws_cli_script object we will lose track of whats has been deleted
  // We need to come up with process of tracking file delete
  def deleteEntityLineage(entityName: String, userSTS: User, entityType: String = AWS_S3_OBJECT_TYPE)(implicit id: RequestId): Future[Done] = {
    logger.debug(s"Creating Delete lineage for entity $entityName at $timestamp")
    sendSingleMessage(prepareEntityDeleteMessage(userSTS, entityName, entityType).toString, ATLAS_HOOK_TOPIC)
  }

  def createSingleEntity(entityName: String, userSTS: User, entityValues: Option[JsObject])(implicit id: RequestId): Future[Done] = {
    logger.debug(s"Creating lineage for request, $entityName at $timestamp")
    sendSingleMessage(
      prepareEntityFullCreateMessage(userSTS, Vector(entityValues)).toString,
      ATLAS_HOOK_TOPIC)
  }

  def readOrWriteLineage(
      lh: LineageHeaders,
      userSTS: User,
      method: AccessType,
      userIPs: UserIps,
      externalFsPath: Option[String] = None,
      guids: LineageObjectGuids = LineageObjectGuids())(implicit id: RequestId): Future[Done] = {

    val userName = userSTS.userName.value
    val clientHost = userIPs.getRealIpOrClientIp
    val clientType = lh.clientType.getOrElse("generic")
    val bucketObject = lh.bucketObject
    val pseudoDir = lh.pseduoDir.getOrElse(s"${lh.bucket}/")
    val externalPath = externalFsPath.getOrElse("")

    // entity definitions
    val serverEntityJs = serverEntity(clientHost, userName, guids.serverGuid)
    val bucketEntityJs = bucketEntity(lh.bucket, userName, guids.bucketGuid, List.empty)
    val pseudoDirEntityJs = pseudoDirEntity(pseudoDir, lh.bucket, guids.bucketGuid, userName, guids.pseudoDir, lh.classifications.getOrElse(DirClassification(), List.empty))
    val s3ObjectEntityJs = s3ObjectEntity(bucketObject, pseudoDir, guids.pseudoDir, userName, lh.contentType.toString(), guids.objectGuid, lh.metadata, lh.classifications.getOrElse(ObjectClassification(), List.empty))
    val externalPathEntityJs = fsPathEntity(externalPath, userName, externalPath, guids.externalPathGuid)

    method match {
      case Read(_) =>
        logger.debug(s"Creating $method lineage for request for file $bucketObject at $lh.bucket at $timestamp")
        sendSingleMessage(
          prepareEntityFullCreateMessage(userSTS, Vector(serverEntityJs, bucketEntityJs, pseudoDirEntityJs, s3ObjectEntityJs, externalPathEntityJs,
            processEntity(s"${clientType}_$timestamp", userName, method.rangerName,
              clientHost, guids.serverGuid,
              bucketObject.getOrElse(pseudoDir), bucketObject.map(_ => AWS_S3_OBJECT_TYPE).getOrElse(AWS_S3_PSEUDO_DIR_TYPE),
              bucketObject.map(_ => guids.objectGuid).getOrElse(guids.pseudoDir),
              externalPath, HADOOP_FS_PATH, guids.externalPathGuid, guids.processGuid)))
            .toString, ATLAS_HOOK_TOPIC)

      case Write(_) if externalPath.length > 0 =>
        logger.debug(s"Creating $method lineage for request for file $bucketObject at $lh.bucket at $timestamp")
        sendSingleMessage(
          prepareEntityFullCreateMessage(userSTS, Vector(serverEntityJs, bucketEntityJs, pseudoDirEntityJs, s3ObjectEntityJs, externalPathEntityJs,
            processEntity(s"${clientType}_$timestamp", userName, method.rangerName,
              clientHost, guids.serverGuid,
              externalPath, HADOOP_FS_PATH, guids.externalPathGuid,
              bucketObject.getOrElse(pseudoDir), bucketObject.map(_ => AWS_S3_OBJECT_TYPE).getOrElse(AWS_S3_PSEUDO_DIR_TYPE),
              bucketObject.map(_ => guids.objectGuid).getOrElse(guids.pseudoDir), guids.processGuid)))
            .toString, ATLAS_HOOK_TOPIC)

      case _ => Future(Done)
    }
  }

  def lineageForCopyOperation(
      lh: LineageHeaders,
      userSTS: User,
      method: AccessType,
      userIPs: UserIps,
      srcGuids: LineageObjectGuids = LineageObjectGuids(),
      destGuids: LineageObjectGuids = LineageObjectGuids())(implicit id: RequestId): Future[Done] = {

    val userName = userSTS.userName.value
    val clientHost = userIPs.getRealIpOrClientIp
    val clientType = lh.clientType.getOrElse("generic")
    val bucketObject = lh.bucketObject
    val pseudoDir = lh.pseduoDir.getOrElse(s"${lh.bucket}/")
    val objectNameFromCopySrc = getObjectName(lh.copySource.getOrElse(""))
    val bucketNameFromCopySrc = getBucketName(lh.copySource.getOrElse(""))
    val pseudoDirFromCopySrc = getPathDir(lh.copySource.getOrElse("")).getOrElse("")

    // entity definitions
    val serverEntityJs = serverEntity(clientHost, userName, srcGuids.serverGuid)
    val srcBucketEntityJs = bucketEntity(bucketNameFromCopySrc, userName, srcGuids.bucketGuid, List.empty)
    val srcPseudoDirEntityJs = pseudoDirEntity(pseudoDirFromCopySrc, bucketNameFromCopySrc, srcGuids.bucketGuid, userName, srcGuids.pseudoDir, lh.classifications.getOrElse(DirClassification(), List.empty))
    val srcS3ObjectEntityJs = s3ObjectEntity(objectNameFromCopySrc, pseudoDirFromCopySrc, srcGuids.pseudoDir, userName, lh.contentType.toString(), srcGuids.objectGuid, lh.metadata, lh.classifications.getOrElse(BucketClassification(), List.empty))
    val destBucketEntityJs = bucketEntity(lh.bucket, userName, destGuids.bucketGuid, List.empty)
    val destPseudoDirEntityJs =
      if (bucketNameFromCopySrc == lh.bucket) {
        pseudoDirEntity(pseudoDir, bucketNameFromCopySrc, srcGuids.bucketGuid, userName, destGuids.pseudoDir, lh.classifications.getOrElse(DirClassification(), List.empty))
      } else {
        pseudoDirEntity(pseudoDir, lh.bucket, destGuids.bucketGuid, userName, destGuids.pseudoDir, lh.classifications.getOrElse(DirClassification(), List.empty))
      }
    val destS3ObjectEntityJs = s3ObjectEntity(bucketObject, pseudoDir, destGuids.pseudoDir, userName, lh.contentType.toString(), destGuids.objectGuid, lh.metadata, lh.classifications.getOrElse(ObjectClassification(), List.empty))
    val copyProcessEntityJs = processEntity(s"${clientType}_$timestamp", userName, method.rangerName, clientHost, srcGuids.serverGuid,
      objectNameFromCopySrc.getOrElse(pseudoDirFromCopySrc), objectNameFromCopySrc.map(_ => AWS_S3_OBJECT_TYPE).getOrElse(AWS_S3_PSEUDO_DIR_TYPE),
      objectNameFromCopySrc.map(_ => srcGuids.objectGuid).getOrElse(srcGuids.pseudoDir),
      bucketObject.getOrElse(pseudoDir), bucketObject.map(_ => AWS_S3_OBJECT_TYPE).getOrElse(AWS_S3_PSEUDO_DIR_TYPE),
      bucketObject.map(_ => destGuids.objectGuid).getOrElse(destGuids.pseudoDir), destGuids.processGuid)

    if (bucketNameFromCopySrc == lh.bucket) {
      sendSingleMessage(
        prepareEntityFullCreateMessage(
          userSTS,
          Vector(serverEntityJs, srcBucketEntityJs, srcPseudoDirEntityJs, srcS3ObjectEntityJs, destPseudoDirEntityJs, destS3ObjectEntityJs, copyProcessEntityJs)).toString(),
        ATLAS_HOOK_TOPIC)
    } else {
      sendSingleMessage(
        prepareEntityFullCreateMessage(
          userSTS,
          Vector(serverEntityJs, srcBucketEntityJs, srcPseudoDirEntityJs, srcS3ObjectEntityJs, destBucketEntityJs, destPseudoDirEntityJs, destS3ObjectEntityJs, copyProcessEntityJs)).toString(),
        ATLAS_HOOK_TOPIC)
    }
  }
}
