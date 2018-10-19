package com.ing.wbaa.airlock.proxy.provider

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{ ContentType, HttpMethods, HttpRequest }
import akka.stream.Materializer
import com.ing.wbaa.airlock.proxy.config.AtlasSettings
import com.ing.wbaa.airlock.proxy.data._
import com.ing.wbaa.airlock.proxy.provider.LineageProviderAtlas.LineageProviderAtlasException
import com.ing.wbaa.airlock.proxy.provider.atlas.Model.{ Bucket, BucketAttributes, Classification, Entities, FileAttributes, IngestedFile, Ingestion, IngestionAttributes, Server, ServerAttributes, guidRef }
import com.ing.wbaa.airlock.proxy.provider.atlas.RestClient
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{ ExecutionContext, Future }

trait LineageProviderAtlas extends LazyLogging with RestClient {

  protected[this] implicit def system: ActorSystem
  protected[this] implicit def executionContext: ExecutionContext
  protected[this] implicit def materializer: Materializer

  protected[this] implicit def atlasSettings: AtlasSettings

  private def serverEntities(userSTS: String, host: String) =
    Entities(Seq(Server(
      "Server",
      userSTS,
      ServerAttributes(host, host, host, host),
      Seq(Classification("staging_node")))))

  private def bucketEntities(userSTS: String, bucket: String) =
    Entities(Seq(Bucket(
      "Bucket",
      userSTS,
      BucketAttributes(bucket, bucket, bucket),
      Seq(Classification("customer_PII")))))

  private def fileEntities(
      serverGuid: String,
      bucketGuid: String,
      bucketObject: String,
      userSTS: String,
      inputGuid: String,
      inputType: String,
      outputGuid: String,
      outputType: String,
      contentType: ContentType) = {
    Entities(Seq(IngestedFile(
      "DataFile",
      userSTS,
      FileAttributes(bucketObject, bucketObject, bucketObject, contentType.mediaType.value,
        guidRef(bucketGuid, "Bucket"),
        List(guidRef(inputGuid, inputType)),
        List(guidRef(outputGuid, outputType)),
        Seq(Classification("customer_PII"))))))
  }

  private def processEntities(
      serverGuid: String,
      bucketGuid: String,
      fileGuid: String,
      userSTS: String,
      method: String,
      inputGuid: String,
      inputType: String,
      outputGuid: String,
      outputType: String,
      timestamp: Long) = {
    Entities(Seq(Ingestion(
      "aws_cli_script",
      userSTS,
      IngestionAttributes(s"aws_cli_${timestamp}", s"aws_cli_${timestamp}", method, userSTS,
        guidRef(serverGuid, "Server"),
        List(guidRef(inputGuid, inputType)),
        List(guidRef(outputGuid, outputType))))))
  }


  // move to LineageHelpers and split to postEntity
  private def postEnities(userSTS: String, host: String, bucket: String, bucketObject: String, method: String, contentType: ContentType, timestamp: Long): Future[LineagePostGuidResponse] = {
    for {
      serverGuid <- postData(serverEntities(userSTS, host).toJson)
      bucketGuid <- postData(bucketEntities(userSTS, bucket).toJson)
      fileGuid <- method match {
        case access if access == "read" =>
          postData(
            fileEntities(serverGuid, bucketGuid, bucketObject, userSTS, bucketGuid, "Bucket", serverGuid, "Server", contentType).toJson)
        case access if access == "write" =>
          postData(
            fileEntities(serverGuid, bucketGuid, bucketObject, userSTS, serverGuid, "Server", bucketGuid, "Bucket", contentType).toJson)
      }
      processGuid <- method match {
        case access if access == "read" =>
          postData(
            processEntities(serverGuid, bucketGuid, fileGuid, userSTS, method, bucketGuid, "Bucket", fileGuid, "DataFile", timestamp).toJson)
        case access if access == "write" =>
          postData(
            processEntities(serverGuid, bucketGuid, fileGuid, userSTS, method, fileGuid, "DataFile", bucketGuid, "Bucket", timestamp).toJson)
      }
    } yield LineagePostGuidResponse(serverGuid, bucketGuid, fileGuid, processGuid)
  }

  // file entity delete
  // for now it is just deleting file entity and no related objects like eg. aws_cli_script, which uploaded or downloaded
  // file to bucket. Once we delete aws_cli_script object we will lose track of whats has been deleted
  // We need to come up with process of tracking file delete
  private def deleteEntities(typeName: String, entityName: String): Future[LineagePostGuidResponse] =
    for {
      entityGuid <- getEntityGUID(typeName, entityName)
      _ <- deleteEntity(entityGuid)

    } yield LineagePostGuidResponse("", "", entityGuid, "")

  // bucket name as partitioning

  // for all filter only on wanted subresource
  // get object
  // put object
  // put object - copy
  // post object (complete multipart)
  // delete on abort multipart
  // delete object

  def createLineageFromRequest(httpRequest: HttpRequest, userSTS: User): Future[LineagePostGuidResponse] = {

    val timestamp = System.currentTimeMillis()
    // createLineageFromRequest is used in RequestHandlerS3 where there is no notion of S3Request. It seems easier to
    // repeat httpRequest parsing here for needed values
    val host = httpRequest.uri.authority.host.address()
    val path = httpRequest.uri.path
    val bucket = path.toString.split("/").toList.lift(1).getOrElse("notDef")
    val bucketObject = s"${path.toString.split("/").toList.lift(2).getOrElse("emptyObject")}"
    val method = httpRequest.method
    val contentType = httpRequest.entity.contentType
    val userName = userSTS.userName.value

    if (bucket != "notDef" && bucketObject != "emptyObject") {
      logger.debug(s"Creating lineage for request ${method.value} file ${bucketObject} in ${bucket} at ${timestamp}")
      method match {
        case HttpMethods.GET | HttpMethods.HEAD =>
          logger.debug(s"Creating Read lineage for request to ${method.value} file ${bucketObject} to ${bucket} at ${timestamp}")
          postEnities(userName, host, bucket, bucketObject, "read", contentType, timestamp)
        case HttpMethods.POST | HttpMethods.PUT =>
          logger.debug(s"Creating Write lineage for request to ${method.value} file ${bucketObject} to ${bucket} at ${timestamp}")
          postEnities(userName, host, bucket, bucketObject, "write", contentType, timestamp)
        case HttpMethods.DELETE =>
          logger.debug(s"Creating Delete lineage for request to ${method} file ${bucketObject} to ${bucket} at ${timestamp}")
          deleteEntities("DataFile", bucketObject)
        case _ => Future.failed(LineageProviderAtlasException("Create lineage failed"))
      }
    } else {
      Future.failed(LineageProviderAtlasException("Create lineage failed"))
    }
  }
}
object LineageProviderAtlas {
  final case class LineageProviderAtlasException(private val message: String, private val cause: Throwable = None.orNull)
    extends Exception(message, cause)
}
