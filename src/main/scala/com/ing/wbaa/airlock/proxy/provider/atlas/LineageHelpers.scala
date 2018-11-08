package com.ing.wbaa.airlock.proxy.provider.atlas

import akka.http.scaladsl.model.{ ContentType, HttpRequest, RemoteAddress }
import com.ing.wbaa.airlock.proxy.data._
import com.ing.wbaa.airlock.proxy.provider.LineageProviderAtlas.LineageProviderAtlasException
import com.ing.wbaa.airlock.proxy.provider.atlas.Model._
import com.typesafe.scalalogging.LazyLogging
import spray.json.JsValue

import scala.concurrent.Future

trait LineageHelpers extends LazyLogging with RestClient {
  val AWS_S3_OBJECT_TYPE = "aws_s3_object"
  val AWS_S3_BUCKET_TYPE = "aws_s3_bucket"
  val AWS_S3_PSEUDO_DIR_TYPE = "aws_s3_pseudo_dir"
  val HADOOP_FS_PATH = "fs_path"
  val AIRLOCK_CLIENT_TYPE = "airlock_client"
  val AIRLOCK_SERVER_TYPE = "server"
  val AIRLOCK_PII = "customer_PII"
  val AIRLOCK_STAGING_NODE = "staging_node"

  private def timestamp: Long = System.currentTimeMillis()

  private def serverEntities(userSTS: String, host: String, classification: String) =
    Entities(Seq(Server(
      AIRLOCK_SERVER_TYPE,
      userSTS,
      ServerAttributes(host, host, host, host),
      Seq(Classification(classification)))))

  private def bucketEntities(bucket: String, classification: String) =
    Entities(Seq(Bucket(
      AWS_S3_BUCKET_TYPE,
      BucketAttributes(bucket, bucket),
      Seq(Classification(classification)))))

  private def pseudoDirEntities(bucketGuid: String, pseudoDir: String) =
    Entities(Seq(PseudoDir(
      AWS_S3_PSEUDO_DIR_TYPE,
      PseudoDirAttributes(pseudoDir, pseudoDir, pseudoDir, guidRef(bucketGuid, AWS_S3_BUCKET_TYPE))
    )))

  private def bucketObjectEntities(
      pseudoDirGuid: String,
      bucketObject: String,
      contentType: ContentType,
      classification: String) = {
    Entities(Seq(BucketObject(
      AWS_S3_OBJECT_TYPE,
      BucketObjectAttributes(bucketObject, bucketObject, contentType.mediaType.value,
        guidRef(pseudoDirGuid, AWS_S3_PSEUDO_DIR_TYPE),
        Seq(Classification(classification))))))
  }

  private def processEntities(
      serverGuid: String,
      bucketGuid: String,
      fileGuid: String,
      userSTS: String,
      method: String,
      inputs: List[guidRef],
      outputs: List[guidRef],
      clientType: String,
      timestamp: Long) = {
    Entities(Seq(ClientProcess(
      AIRLOCK_CLIENT_TYPE,
      userSTS,
      ClientProcessAttributes(s"${clientType}_$timestamp", s"${clientType}_$timestamp", method, userSTS,
        guidRef(serverGuid, AIRLOCK_SERVER_TYPE),
        inputs,
        outputs))))
  }

  private def fsPathEntities(path: String) =
    Entities(Seq(FsPath(
      HADOOP_FS_PATH,
      FsPathAttributes(path, path, path)
    )))

  private def processIn(inputGUID: String, inputType: String) = List(guidRef(inputGUID, inputType))

  private def processOut(outputGUID: String, outputType: String) = List(guidRef(outputGUID, outputType))

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

  // file entity delete
  // for now it is just deleting file entity and no related objects like eg. aws_cli_script, which uploaded or downloaded
  // file to bucket. Once we delete aws_cli_script object we will lose track of whats has been deleted
  // We need to come up with process of tracking file delete
  def deleteLineage(lh: LineageHeaders): Future[LineageGuidResponse] = {
    logger.debug(s"Creating Delete lineage for request to ${lh.method} file ${lh.bucketObject} at ${lh.bucket} at $timestamp")
    for {
      entityGuidResponse <- getEntityGUID(AWS_S3_OBJECT_TYPE, lh.bucketObject.get)
      _ <- deleteEntity(entityGuidResponse.entityGUID)

    } yield entityGuidResponse
  }

  private def lineageGuidFuture(entity: String, entityType: String, entities: JsValue): Future[LineageGuidResponse] =
    for {
      guid <- getEntityGUID(entityType, entity)
      newGuid <- if (guid.entityGUID.isEmpty) {
        postData(entities)
      } else Future(guid)
    } yield (newGuid)

  def readOrWriteLineage(lh: LineageHeaders, userSTS: User, method: AccessType, clientIPAddress: RemoteAddress, externalFsPath: Option[String] = None): Future[LineagePostGuidResponse] = {
    val userName = userSTS.userName.value
    val clientHost = clientIPAddress.getAddress().get().getHostAddress
    val clientType = lh.clientType.getOrElse("generic")
    val bucketObject = lh.bucketObject.getOrElse("emptyObject")
    val pseudoDir = lh.pseduoDir.getOrElse(s"${lh.bucket}/")

    logger.debug(s"Creating $method lineage for request for file $bucketObject at $lh.bucket at $timestamp")
    for {
      serverGuid <- lineageGuidFuture(clientHost, AIRLOCK_SERVER_TYPE, serverEntities(userName, clientHost, AIRLOCK_STAGING_NODE).toJson).map(_.entityGUID)
      bucketGuid <- lineageGuidFuture(lh.bucket, AWS_S3_BUCKET_TYPE, bucketEntities(lh.bucket, AIRLOCK_PII).toJson).map(_.entityGUID)
      pseudoDirGuid <- lineageGuidFuture(pseudoDir, AWS_S3_PSEUDO_DIR_TYPE, pseudoDirEntities(bucketGuid, pseudoDir).toJson).map(_.entityGUID)
      objectGuid <- lineageGuidFuture(bucketObject, AWS_S3_OBJECT_TYPE, bucketObjectEntities(pseudoDirGuid, bucketObject, lh.contentType, AIRLOCK_PII).toJson).map(_.entityGUID)
      fsPathGuid <- externalFsPath match {
        case Some(fsPath) => lineageGuidFuture(fsPath, HADOOP_FS_PATH, fsPathEntities(fsPath).toJson).map(_.entityGUID)
        case None         => Future("")
      }
      processGuid <- method match {
        case Read =>
          postData(
            processEntities(
              serverGuid, bucketGuid, objectGuid, userName, method.rangerName, processIn(objectGuid, AWS_S3_OBJECT_TYPE), processOut(fsPathGuid, HADOOP_FS_PATH), clientType, timestamp
            ).toJson)
            .map(r => r.entityGUID)

        case Write if fsPathGuid.length > 0 =>
          postData(
            processEntities(
              serverGuid, bucketGuid, objectGuid, userName, method.rangerName, processIn(fsPathGuid, HADOOP_FS_PATH), processOut(objectGuid, AWS_S3_OBJECT_TYPE), clientType, timestamp
            ).toJson)
            .map(r => r.entityGUID)

        case Write =>
          postData(
            processEntities(
              serverGuid, bucketGuid, objectGuid, userName, method.rangerName, processIn(objectGuid, AWS_S3_OBJECT_TYPE), processOut(pseudoDirGuid, AWS_S3_PSEUDO_DIR_TYPE), clientType, timestamp
            ).toJson)
            .map(r => r.entityGUID)

        case _ => Future.failed(LineageProviderAtlasException("Lineage method not supported"))
      }
    } yield LineagePostGuidResponse(serverGuid, bucketGuid, pseudoDirGuid, objectGuid, processGuid)
  }

  def lineageForCopyOperation(lh: LineageHeaders, userSTS: User, method: AccessType, clientIPAddress: RemoteAddress): Future[LineagePostGuidResponse] = {
    val userName = userSTS.userName.value
    val clientHost = clientIPAddress.getAddress().get().getHostAddress
    val clientType = lh.clientType.getOrElse("generic")
    val bucketObject = lh.bucketObject.getOrElse("emptyObject")
    val pseudoDir = lh.pseduoDir.getOrElse(s"${lh.bucket}/")
    val objectNameFromCopySrc = lh.copySource.get
    val bucketNameFromCopySrc = lh.copySource.get.split("/").head
    val pseudoDirFromCopySrc = {
      val pathArray = lh.copySource.get.split("/").dropRight(1)
      if (pathArray.length > 2)
        pathArray.mkString("/")
      else
        pathArray.mkString + "/"
    }

    for {
      serverGuid <- lineageGuidFuture(clientHost, AIRLOCK_SERVER_TYPE, serverEntities(userName, clientHost, AIRLOCK_STAGING_NODE).toJson).map(_.entityGUID)
      // source objects
      srcBucketGuid <- lineageGuidFuture(bucketNameFromCopySrc, AWS_S3_BUCKET_TYPE, bucketEntities(bucketNameFromCopySrc, AIRLOCK_PII).toJson).map(_.entityGUID)
      srcPseudoDirGuid <- lineageGuidFuture(pseudoDirFromCopySrc, AWS_S3_PSEUDO_DIR_TYPE, pseudoDirEntities(srcBucketGuid, pseudoDirFromCopySrc).toJson).map(_.entityGUID)
      srcObject <- lineageGuidFuture(objectNameFromCopySrc, AWS_S3_OBJECT_TYPE, bucketObjectEntities(srcPseudoDirGuid, objectNameFromCopySrc, lh.contentType, AIRLOCK_PII).toJson).map(_.entityGUID)
      // destination objects
      destBucketGuid <- lineageGuidFuture(lh.bucket, AWS_S3_BUCKET_TYPE, bucketEntities(lh.bucket, AIRLOCK_PII).toJson).map(_.entityGUID)
      destPseudoDirGuid <- lineageGuidFuture(pseudoDir, AWS_S3_PSEUDO_DIR_TYPE, pseudoDirEntities(destBucketGuid, pseudoDir).toJson).map(_.entityGUID)
      destObjectGuid <- lineageGuidFuture(bucketObject, AWS_S3_OBJECT_TYPE, bucketObjectEntities(destPseudoDirGuid, bucketObject, lh.contentType, AIRLOCK_PII).toJson).map(_.entityGUID)
      // process
      processGuid <- postData(processEntities(
        serverGuid, destBucketGuid, destObjectGuid, userName, method.rangerName,
        processIn(srcObject, AWS_S3_OBJECT_TYPE),
        processOut(destObjectGuid, AWS_S3_OBJECT_TYPE),
        clientType, timestamp
      ).toJson)
        .map(r => r.entityGUID)
    } yield LineagePostGuidResponse(serverGuid, destBucketGuid, destPseudoDirGuid, destObjectGuid, processGuid)
  }
}
