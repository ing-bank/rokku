package com.ing.wbaa.gargoyle.proxy.provider

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{ ContentType, HttpMethods, HttpRequest }
import com.ing.wbaa.gargoyle.proxy.config.GargoyleAtlasSettings
import com.ing.wbaa.gargoyle.proxy.data._
import com.ing.wbaa.gargoyle.proxy.provider.Atlas.Model.{ Bucket, BucketAttributes, Classification, Entities, FileAttributes, IngestedFile, Ingestion, IngestionAttributes, Server, ServerAttributes, guidRef }
import com.ing.wbaa.gargoyle.proxy.provider.Atlas.RestClient
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{ ExecutionContext, Future }

trait LineageProviderAtlas extends LazyLogging {

  protected[this] implicit def system: ActorSystem
  protected[this] implicit def executionContext: ExecutionContext
  protected[this] implicit def atlasSettings: GargoyleAtlasSettings

  private def serverEntities(userSTS: String, host: String) =
    Entities(Seq(Server("Server", userSTS, ServerAttributes(host, host, host, host), Seq(Classification("staging_node")))))

  private def bucketEntities(userSTS: String, bucket: String) =
    Entities(Seq(Bucket("Bucket", userSTS, BucketAttributes(bucket, bucket, bucket), Seq(Classification("customer_PII")))))

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
        guidRef(serverGuid, "Server"),
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

  def postEnities(userSTS: String, host: String, bucket: String, bucketObject: String, method: String, contentType: ContentType, timestamp: Long)(implicit client: RestClient): Future[Option[(String, String, String, String)]] = {
    for {
      serverGuid <- client.postData(serverEntities(userSTS, host).toJson)
      bucketGuid <- client.postData(bucketEntities(userSTS, bucket).toJson)
      fileGuid <- method match {
        case access if access == "read"  => client.postData(fileEntities(serverGuid, bucketGuid, bucketObject, userSTS, bucketGuid, "Bucket", serverGuid, "Server", contentType).toJson)
        case access if access == "write" => client.postData(fileEntities(serverGuid, bucketGuid, bucketObject, userSTS, serverGuid, "Server", bucketGuid, "Bucket", contentType).toJson)
      }
      processGuid <- method match {
        case access if access == "read"  => client.postData(processEntities(serverGuid, bucketGuid, fileGuid, userSTS, method, bucketGuid, "Bucket", fileGuid, "DataFile", timestamp).toJson)
        case access if access == "write" => client.postData(processEntities(serverGuid, bucketGuid, fileGuid, userSTS, method, fileGuid, "DataFile", bucketGuid, "Bucket", timestamp).toJson)
      }
    } yield Some(Tuple4(serverGuid, bucketGuid, fileGuid, processGuid))
  }

  // file entity
  def deleteEntities(typeName: String, entityName: String)(implicit client: RestClient): Future[Option[(String, String, String, String)]] = {
    for {
      entityGuid <- client.getEntityGUID(typeName, entityName)
      guid <- client.deleteEntity(entityGuid)
    } yield (Some(Tuple4("", "", entityGuid, "")))
  }

  def createLineageFromRequest(httpRequest: HttpRequest, userSTS: User): Future[Option[(String, String, String, String)]] = {

    implicit val client = new RestClient()
    val dateFormatted = DateTimeFormatter.ofPattern("dd-MM-yyyy").format(LocalDateTime.now())
    val host = httpRequest.uri.authority.host.address()
    val path = httpRequest.uri.path
    val bucket = path.toString.split("/").toList.lift(1).getOrElse("notDef")
    val bucketObject = s"${path.toString.split("/").toList.lift(1).getOrElse("emptyObject")}_${dateFormatted}"
    val method = httpRequest.method
    val contentType = httpRequest.entity.contentType
    val userName = userSTS.userName.value
    val timestamp = System.currentTimeMillis()

    if (bucket != "notDef" && bucketObject != "emptyObject") {
      logger.debug(s"Creating lineage for request to ${method} file ${bucketObject} in ${bucket} at ${timestamp}")
      method match {
        case HttpMethods.GET =>
          logger.debug(s"Creating Read lineage for request to ${method.value} file ${bucketObject} to ${bucket} at ${timestamp}")
          postEnities(userName, host, bucket, bucketObject, method.value, contentType, timestamp)
        case HttpMethods.POST => // add condition to prevent dobule lineage method from request
          logger.debug(s"Creating Write lineage for request to ${method.value} file ${bucketObject} to ${bucket} at ${timestamp}")
          postEnities(userName, host, bucket, bucketObject, method.value, contentType, timestamp)
        case HttpMethods.DELETE =>
          logger.debug(s"Creating Delete lineage for request to ${method} file ${bucketObject} to ${bucket} at ${timestamp}")
          //todo: add other Entities
          deleteEntities("DataFile", bucketObject)
        case _ => Future(None)
      }
    } else {
      Future(None)
    }
  }
}
