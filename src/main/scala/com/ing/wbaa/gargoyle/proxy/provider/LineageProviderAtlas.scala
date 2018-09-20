package com.ing.wbaa.gargoyle.proxy.provider

import akka.actor.ActorSystem
import akka.http.scaladsl.model.ContentType
import akka.http.scaladsl.model.Uri.Authority
import akka.stream.Materializer
import com.ing.wbaa.gargoyle.proxy.data.S3Request
import com.ing.wbaa.gargoyle.proxy.provider.Atlas.Model.{ Bucket, BucketAttributes, Classification, Entities, FileAttributes, IngestedFile, Ingestion, IngestionAttributes, Server, ServerAttributes, guidRef }
import com.ing.wbaa.gargoyle.proxy.provider.Atlas.RestClient
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{ ExecutionContext, Future }

trait LineageProviderAtlas extends LazyLogging {

  protected[this] implicit def system: ActorSystem
  protected[this] implicit def executionContext: ExecutionContext
  protected[this] implicit def materializer: Materializer

  def createLineageFromRequest(s3Request: S3Request, authority: Authority, contentType: ContentType): Future[Option[(String, String, String, String)]] = {

    val client = new RestClient()

    val userSTS = s3Request.credential.accessKey.value
    val host = authority.host.address()
    val bucket = s3Request.bucket.getOrElse("notDef")
    val bucketObject = s3Request.bucketObjectRoot.getOrElse("emptyObject")
    val method = s3Request.accessType.rangerName

    //create entities
    val serverEntity = Server("server", userSTS, ServerAttributes(host, host, host, host), Seq(Classification("storage_node")))
    val bucketEntity = Bucket("Bucket", userSTS, BucketAttributes(bucket, bucket, bucket), Seq(Classification("customer_PII")))

    def fileEntity(serverGuid: String, bucketGuid: String) =
      IngestedFile(
        "DataFile",
        userSTS,
        FileAttributes(bucketObject, bucketObject, bucketObject, contentType.mediaType.value,
          guidRef(bucketGuid, "Bucket"),
          guidRef(serverGuid, "server"),
          List(guidRef(serverGuid, "server")),
          Seq(Classification("customer_PII"))))

    def processEntity(serverGuid: String, bucketGuid: String, fileGuid: String) =
      Ingestion(
        "aws_cli_script",
        userSTS,
        IngestionAttributes("aws_cli_s3api", "aws_cli_s3api", method, userSTS,
          guidRef(serverGuid, "server"),
          List(guidRef(fileGuid, "DataFile")),
          List(guidRef(bucketGuid, "Bucket"))
        ))

    if (bucket != "notDef") {
      logger.debug(s"Creating lineage for request to ${method} file ${bucketObject} to ${bucket}")

      val guidResponse = for {
        serverGuid <- client.postData(Entities(Seq(serverEntity)).toJson)
        bucketGuid <- client.postData(Entities(Seq(bucketEntity)).toJson)
        fileGuid <- if(bucketObject != "emptyObject") client.postData(Entities(Seq(fileEntity(serverGuid, bucketGuid))).toJson)
        processGuid <- client.postData(Entities(Seq(processEntity(serverGuid, bucketGuid, fileGuid))).toJson)
      } yield Some(Tuple4(serverGuid, bucketGuid, fileGuid, processGuid))
      guidResponse
    } else { Future(None) }

  }
}
