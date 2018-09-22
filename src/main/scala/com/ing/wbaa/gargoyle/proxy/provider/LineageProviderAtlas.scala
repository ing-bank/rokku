package com.ing.wbaa.gargoyle.proxy.provider

import akka.actor.ActorSystem
import akka.http.scaladsl.model.ContentType
import akka.http.scaladsl.model.Uri.Authority
import akka.stream.Materializer
import com.ing.wbaa.gargoyle.proxy.config.GargoyleAtlasSettings
import com.ing.wbaa.gargoyle.proxy.data._
import com.ing.wbaa.gargoyle.proxy.provider.Atlas.Model.{ Bucket, BucketAttributes, Classification, Entities, FileAttributes, IngestedFile, Ingestion, IngestionAttributes, Server, ServerAttributes, guidRef }
import com.ing.wbaa.gargoyle.proxy.provider.Atlas.RestClient
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{ ExecutionContext, Future }

trait LineageProviderAtlas extends LazyLogging {

  protected[this] implicit def system: ActorSystem
  protected[this] implicit def executionContext: ExecutionContext
  protected[this] implicit def materializer: Materializer

  protected[this] implicit def atlasSettings: GargoyleAtlasSettings

  //create entities
  private def serverEntity(userSTS: String, host: String) =
    Server("Server", userSTS, ServerAttributes(host, host, host, host), Seq(Classification("staging_node")))

  private def bucketEntity(userSTS: String, bucket: String) =
    Bucket("Bucket", userSTS, BucketAttributes(bucket, bucket, bucket), Seq(Classification("customer_PII")))

  private def fileEntity(
      serverGuid: String,
      bucketGuid: String,
      bucketObject: String,
      userSTS: String,
      inputGuid: String,
      inputType: String,
      outputGuid: String,
      outputType: String,
      contentType: ContentType) = {
    IngestedFile(
      "DataFile",
      userSTS,
      FileAttributes(bucketObject, bucketObject, bucketObject, contentType.mediaType.value,
        guidRef(bucketGuid, "Bucket"),
        guidRef(serverGuid, "Server"),
        List(guidRef(inputGuid, inputType)),
        List(guidRef(outputGuid, outputType)),
        Seq(Classification("customer_PII"))))
  }

  private def processEntity(
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
    Ingestion(
      "aws_cli_script",
      userSTS,
      IngestionAttributes(s"aws_cli_${timestamp}", s"aws_cli_${timestamp}", method, userSTS,
        guidRef(serverGuid, "Server"),
        List(guidRef(inputGuid, inputType)),
        List(guidRef(outputGuid, outputType))
      ))
  }

  def createLineageFromRequest(s3Request: S3Request, authority: Authority, contentType: ContentType): Future[Option[(String, String, String, String)]] = {

    val client = new RestClient()

    val userSTS = s3Request.credential.accessKey.value
    val host = authority.host.address()
    val bucket = s3Request.bucket.getOrElse("notDef")
    val bucketObject = s3Request.bucketObjectRoot.getOrElse("emptyObject")
    val method = s3Request.accessType.rangerName

    val timestamp = System.currentTimeMillis()

    if (bucket != "notDef" && bucketObject != "emptyObject") {
      logger.debug(s"Creating lineage for request to ${method} file ${bucketObject} to ${bucket} at ${timestamp}")
      s3Request.accessType match {
        case Read =>
          logger.debug(s"Creating Read lineage for request to ${method} file ${bucketObject} to ${bucket} at ${timestamp}")
          for {
            serverGuid <- client.postData(Entities(Seq(serverEntity(userSTS, host))).toJson)
            bucketGuid <- client.postData(Entities(Seq(bucketEntity(userSTS, bucket))).toJson)
            fileGuid <- client.postData(Entities(Seq(fileEntity(serverGuid, bucketGuid, bucketObject, userSTS, bucketGuid, "Bucket", serverGuid, "Server", contentType))).toJson)
            processGuid <- client.postData(Entities(Seq(processEntity(serverGuid, bucketGuid, fileGuid, userSTS, method, bucketGuid, "Bucket", fileGuid, "DataFile", timestamp))).toJson)
          } yield Some(Tuple4(serverGuid, bucketGuid, fileGuid, processGuid))
        case Write => // add condition to prevent dobule lineage
          logger.debug(s"Creating Write lineage for request to ${method} file ${bucketObject} to ${bucket} at ${timestamp}")
          for {
            serverGuid <- client.postData(Entities(Seq(serverEntity(userSTS, host))).toJson)
            bucketGuid <- client.postData(Entities(Seq(bucketEntity(userSTS, bucket))).toJson)
            fileGuid <- client.postData(Entities(Seq(fileEntity(serverGuid, bucketGuid, bucketObject, userSTS, serverGuid, "Server", bucketGuid, "Bucket", contentType))).toJson)
            processGuid <- client.postData(Entities(Seq(processEntity(serverGuid, bucketGuid, fileGuid, userSTS, method, fileGuid, "DataFile", bucketGuid, "Bucket", timestamp))).toJson)
          } yield Some(Tuple4(serverGuid, bucketGuid, fileGuid, processGuid))
        case Delete =>
          logger.debug(s"Creating Delete lineage for request to ${method} file ${bucketObject} to ${bucket} at ${timestamp}")
          for {
            entityGuid <- client.getEntityGUID("DataFile", bucketObject)
            guid <-
              logger.debug(s"Invoking delete")
              client.deleteEntity(entityGuid)
          } yield (Some(Tuple4("", "", "", "")))
        case _ => Future(None)
      }
    } else {
      Future(None)
    }
  }
}
