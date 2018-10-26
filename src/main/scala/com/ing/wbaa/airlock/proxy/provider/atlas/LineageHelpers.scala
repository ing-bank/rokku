package com.ing.wbaa.airlock.proxy.provider.atlas

import akka.http.scaladsl.model.{ ContentType, HttpRequest }
import com.ing.wbaa.airlock.proxy.data._
import com.ing.wbaa.airlock.proxy.provider.LineageProviderAtlas.LineageProviderAtlasException
import com.ing.wbaa.airlock.proxy.provider.atlas.Model._
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Future

trait LineageHelpers extends LazyLogging with RestClient {
  val AWS_S3_OBJECT_TYPE = "aws_s3_object"
  val AWS_S3_BUCKET_TYPE = "aws_s3_bucket"
  val AWS_S3_PSEUDO_DIR_TYPE = "aws_s3_pseudo_dir"
  val AIRLOCK_CLIENT_TYPE = "airlock_client"
  val AIRLOCK_SERVER_TYPE = "server"
  val AIRLOCK_PII = "customer_PII"
  val AIRLOCK_STAGING_NODE = "staging_node"

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
        Some(path.drop(1).dropRight(1).mkString("/"))
      else
        None

    LineageHeaders(
      extractHeaderOption(httpRequest, "Remote-Address"),
      path.head,
      pseudoDir,
      path.lastOption,
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
  def deleteEntities(typeName: String, entityName: String): Future[LineageGuidResponse] =
    for {
      entityGuidResponse <- getEntityGUID(typeName, entityName)
      _ <- deleteEntity(entityGuidResponse.entityGUID)

    } yield entityGuidResponse

  def postEnities(
      userSTS: String,
      host: String,
      bucket: String,
      bucketObject: String,
      method: AccessType,
      contentType: ContentType,
      clientType: String,
      timestamp: Long,
      pseudoDir: String): Future[LineagePostGuidResponse] = {

    def processIn(inputGUID: String, inputType: String) = List(guidRef(inputGUID, inputType))
    def processOut(outputGUID: String, outputType: String) = List(guidRef(outputGUID, outputType))

    for {
      serverGuid <- postData(serverEntities(userSTS, host, AIRLOCK_STAGING_NODE).toJson).map(r => r.entityGUID)
      bucketGuid <- postData(bucketEntities(bucket, AIRLOCK_PII).toJson).map(r => r.entityGUID)
      pseudoDirGuid <- postData(pseudoDirEntities(bucketGuid, pseudoDir).toJson).map(r => r.entityGUID)
      objectGuid <- postData(bucketObjectEntities(pseudoDirGuid, bucketObject, contentType, AIRLOCK_PII).toJson).map(r => r.entityGUID)
      processGuid <- method match {
        case Read =>
          postData(
            processEntities(
              serverGuid, bucketGuid, objectGuid, userSTS, method.rangerName, processIn(bucketGuid, AWS_S3_PSEUDO_DIR_TYPE), processOut(objectGuid, AWS_S3_OBJECT_TYPE), clientType, timestamp
            ).toJson)
            .map(r => r.entityGUID)
        case Write =>
          postData(
            processEntities(
              serverGuid, bucketGuid, objectGuid, userSTS, method.rangerName, processIn(objectGuid, AWS_S3_OBJECT_TYPE), processOut(bucketGuid, AWS_S3_PSEUDO_DIR_TYPE), clientType, timestamp
            ).toJson)
            .map(r => r.entityGUID)
        case _ => Future.failed(LineageProviderAtlasException("Lineage method not supported"))
      }
    } yield LineagePostGuidResponse(serverGuid, bucketGuid, pseudoDirGuid, objectGuid, processGuid)
  }
}
