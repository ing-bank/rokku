package com.ing.wbaa.airlock.proxy.provider.atlas

import akka.Done
import akka.http.scaladsl.model.{ HttpRequest, RemoteAddress }
import com.ing.wbaa.airlock.proxy.data._
import com.ing.wbaa.airlock.proxy.handler.LoggerHandlerWithId
import com.ing.wbaa.airlock.proxy.provider.atlas.ModelKafka._
import com.ing.wbaa.airlock.proxy.provider.kafka.EventProducer
import spray.json.JsObject
import com.ing.wbaa.airlock.proxy.data.LineageLiterals._

import scala.concurrent.Future

trait LineageHelpers extends EventProducer {

  private val logger = new LoggerHandlerWithId

  private def timestamp: Long = System.currentTimeMillis()

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

  // file/object/bucket object entity delete
  // for now it is just deleting file entity and no related objects like eg. aws_cli_script, which uploaded or downloaded
  // file to bucket. Once we delete aws_cli_script object we will lose track of whats has been deleted
  // We need to come up with process of tracking file delete
  def deleteEntityLineage(entityName: String, userSTS: User, entityType: String = AWS_S3_OBJECT_TYPE)(implicit id: RequestId): Future[Done] = {
    logger.debug(s"Creating Delete lineage for entity $entityName at $timestamp")
    sendSingleMessage(prepareEntityDeleteMessage(userSTS, entityName, entityType).toString, ATLAS_HOOK_TOPIC)
  }

  def createSingleEntity(entityName: String, userSTS: User, entityValues: JsObject)(implicit id: RequestId): Future[Done] = {
    logger.debug(s"Creating lineage for request, $entityName at $timestamp")
    sendSingleMessage(
      prepareEntityFullCreateMessage(userSTS, Vector(entityValues)).toString,
      ATLAS_HOOK_TOPIC)
  }

  def readOrWriteLineage(
      lh: LineageHeaders,
      userSTS: User,
      method: AccessType,
      clientIPAddress: RemoteAddress,
      externalFsPath: Option[String] = None,
      guids: LineageObjectGuids = LineageObjectGuids())(implicit id: RequestId): Future[Done] = {

    val userName = userSTS.userName.value
    val clientHost = clientIPAddress.getAddress().get().getHostAddress
    val clientType = lh.clientType.getOrElse("generic")
    val bucketObject = lh.bucketObject.getOrElse("emptyObject")
    val pseudoDir = lh.pseduoDir.getOrElse(s"${lh.bucket}/")
    val externalPath = externalFsPath.getOrElse("")

    // entity definitions
    val serverEntityJs = serverEntity(clientHost, userName, guids.serverGuid)
    val bucketEntityJs = bucketEntity(lh.bucket, userName, guids.bucketGuid)
    val pseudoDirEntityJs = pseudoDirEntity(pseudoDir, lh.bucket, guids.bucketGuid, userName, guids.pseudoDir)
    val s3ObjectEntityJs = s3ObjectEntity(bucketObject, pseudoDir, guids.pseudoDir, userName, lh.contentType.toString(), guids.objectGuid)
    val externalPathEntityJs = fsPathEntity(externalPath, userName, externalPath, guids.externalPathGuid)

    method match {
      case Read(_) =>
        logger.debug(s"Creating $method lineage for request for file $bucketObject at $lh.bucket at $timestamp")
        sendSingleMessage(
          prepareEntityFullCreateMessage(userSTS, Vector(serverEntityJs, bucketEntityJs, pseudoDirEntityJs, s3ObjectEntityJs, externalPathEntityJs,
            processEntity(s"${clientType}_$timestamp", userName, method.rangerName,
              clientHost, guids.serverGuid,
              bucketObject, AWS_S3_OBJECT_TYPE, guids.objectGuid,
              externalPath, HADOOP_FS_PATH, guids.externalPathGuid, guids.processGuid)))
            .toString, ATLAS_HOOK_TOPIC)

      case Write(_) if externalPath.length > 0 =>
        logger.debug(s"Creating $method lineage for request for file $bucketObject at $lh.bucket at $timestamp")
        sendSingleMessage(
          prepareEntityFullCreateMessage(userSTS, Vector(serverEntityJs, bucketEntityJs, pseudoDirEntityJs, s3ObjectEntityJs, externalPathEntityJs,
            processEntity(s"${clientType}_$timestamp", userName, method.rangerName,
              clientHost, guids.serverGuid,
              externalPath, HADOOP_FS_PATH, guids.externalPathGuid,
              pseudoDir, AWS_S3_PSEUDO_DIR_TYPE, guids.pseudoDir, guids.processGuid)))
            .toString, ATLAS_HOOK_TOPIC)

      case _ => Future(Done)
    }
  }

  def lineageForCopyOperation(
      lh: LineageHeaders,
      userSTS: User,
      method: AccessType,
      clientIPAddress: RemoteAddress,
      srcGuids: LineageObjectGuids = LineageObjectGuids(),
      destGuids: LineageObjectGuids = LineageObjectGuids())(implicit id: RequestId): Future[Done] = {

    val userName = userSTS.userName.value
    val clientHost = clientIPAddress.getAddress().get().getHostAddress
    val clientType = lh.clientType.getOrElse("generic")
    val bucketObject = lh.bucketObject.getOrElse("emptyObject")
    val pseudoDir = lh.pseduoDir.getOrElse(s"${lh.bucket}/")
    val objectNameFromCopySrc = lh.copySource.getOrElse("")
    val bucketNameFromCopySrc = lh.copySource.getOrElse("").split("/").head
    val pseudoDirFromCopySrc = {
      val pathArray = lh.copySource.getOrElse("").split("/").dropRight(1)
      if (pathArray.length >= 2)
        pathArray.mkString("/")
      else
        pathArray.mkString + "/"
    }
    // entity definitions
    val serverEntityJs = serverEntity(clientHost, userName, srcGuids.serverGuid)
    val srcBucketEntityJs = bucketEntity(bucketNameFromCopySrc, userName, srcGuids.bucketGuid)
    val srcPseudoDirEntityJs = pseudoDirEntity(pseudoDirFromCopySrc, bucketNameFromCopySrc, srcGuids.bucketGuid, userName, srcGuids.pseudoDir)
    val srcS3ObjectEntityJs = s3ObjectEntity(objectNameFromCopySrc, pseudoDirFromCopySrc, srcGuids.pseudoDir, userName, lh.contentType.toString(), srcGuids.objectGuid)
    val destBucketEntityJs = bucketEntity(lh.bucket, userName, destGuids.bucketGuid)
    val destPseudoDirEntityJs =
      if (bucketNameFromCopySrc == lh.bucket) {
        pseudoDirEntity(pseudoDir, bucketNameFromCopySrc, srcGuids.bucketGuid, userName, destGuids.pseudoDir)
      } else {
        pseudoDirEntity(pseudoDir, lh.bucket, destGuids.bucketGuid, userName, destGuids.pseudoDir)
      }
    val destS3ObjectEntityJs = s3ObjectEntity(bucketObject, pseudoDir, destGuids.pseudoDir, userName, lh.contentType.toString(), destGuids.objectGuid)
    val copyProcessEntityJs = processEntity(s"${clientType}_$timestamp", userName, method.rangerName, clientHost, srcGuids.serverGuid,
      objectNameFromCopySrc, AWS_S3_OBJECT_TYPE, srcGuids.objectGuid,
      bucketObject, AWS_S3_OBJECT_TYPE, destGuids.objectGuid, destGuids.processGuid)

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
