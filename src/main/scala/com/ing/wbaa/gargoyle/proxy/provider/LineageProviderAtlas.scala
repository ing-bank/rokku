package com.ing.wbaa.gargoyle.proxy.provider

import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpRequest
import akka.stream.Materializer
import com.ing.wbaa.gargoyle.proxy.provider.Atlas.Model.{ Bucket, BucketAttributes, Classification, Entities, FileAttributes, IngestedFile, Ingestion, IngestionAttributes, Server, ServerAttributes, guidRef }
import com.ing.wbaa.gargoyle.proxy.provider.Atlas.RestClient

import scala.concurrent.{ ExecutionContext, Future }

trait LineageProviderAtlas {

  protected[this] implicit def system: ActorSystem
  protected[this] implicit def executionContext: ExecutionContext
  protected[this] implicit def materializer: Materializer

  def createLineageFromRequest(httpRequest: HttpRequest): Future[(String, String, String, String)] = {

    val client = new RestClient()

    val userSTS = "Adam" // replace with proxy val
    val host = httpRequest.uri.authority.toString()
    val hostIP = httpRequest.uri.authority.host.address()
    val bucketPath = httpRequest.uri.path.toString() // extract file / bucket etc.
    val method = httpRequest.method.value

    //create entities
    val server = Server("server", userSTS, ServerAttributes(host, host, host, hostIP), Seq(Classification("storage_node")))
    val bucket = Bucket("Bucket", userSTS, BucketAttributes(bucketPath, bucketPath, bucketPath), Seq(Classification("customer_PII")))

    val responseJsons = for {
      serverGuid <- client.postData(Entities(Seq(server)).toJson)
      bucketGuid <- client.postData(Entities(Seq(bucket)).toJson)
      fileGuid <- client.postData(Entities(Seq(
        IngestedFile("DataFile", userSTS, FileAttributes(bucketPath, bucketPath, bucketPath, "notDef",
          guidRef(bucketGuid, "Bucket"),
          guidRef(serverGuid, "server"),
          List(guidRef(serverGuid, "server")),
          Seq(Classification("customer_PII"))
        )))).toJson)
      processGuid <- client.postData(Entities(Seq(
        Ingestion("aws_cli_script", userSTS, IngestionAttributes("aws_cli_s3api", "aws_cli_s3api", method, userSTS,
          guidRef(serverGuid, "server"),
          List(guidRef(fileGuid, "DataFile")),
          List(guidRef(bucketGuid, "Bucket"))
        ))
      )).toJson)
    } yield (serverGuid, bucketGuid, fileGuid, processGuid)

    responseJsons
  }
}
