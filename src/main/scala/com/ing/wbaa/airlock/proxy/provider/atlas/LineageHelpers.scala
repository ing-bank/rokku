package com.ing.wbaa.airlock.proxy.provider.atlas

import akka.http.scaladsl.model.{ ContentType, HttpRequest }
import com.ing.wbaa.airlock.proxy.data.{ LineageGuidResponse, LineageHeaders, LineagePostGuidResponse }
import com.ing.wbaa.airlock.proxy.provider.atlas.Model._
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Future

trait LineageHelpers extends LazyLogging with RestClient {

  private def serverEntities(userSTS: String, host: String, classification: String) =
    Entities(Seq(Server(
      "Server",
      userSTS,
      ServerAttributes(host, host, host, host),
      Seq(Classification(classification)))))

  private def bucketEntities(userSTS: String, bucket: String, classification: String) =
    Entities(Seq(Bucket(
      "Bucket",
      userSTS,
      BucketAttributes(bucket, bucket, bucket),
      Seq(Classification(classification)))))

  private def fileEntities(
      bucketGuid: String,
      bucketObject: String,
      userSTS: String,
      contentType: ContentType,
      classification: String) = {
    Entities(Seq(IngestedFile(
      "DataFile",
      userSTS,
      FileAttributes(bucketObject, bucketObject, bucketObject, contentType.mediaType.value,
        guidRef(bucketGuid, "Bucket"),
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
    Entities(Seq(Ingestion(
      "airlock_client",
      userSTS,
      IngestionAttributes(s"${clientType}_$timestamp", s"${clientType}_$timestamp", method, userSTS,
        guidRef(serverGuid, "Server"),
        inputs,
        outputs))))
  }

  private def extractClient(userAgent: String): String =
    """(\S+)/\S+""".r
      .findFirstMatchIn(userAgent)
      .map(_ group 1).getOrElse("generic")

  private def extractHeaderOption(httpRequest: HttpRequest, header: String): Option[String] =
    if (httpRequest.getHeader(header).isPresent)
      Some(httpRequest.getHeader(header).get().value())
    else
      None

  def getLineageHeaders(httpRequest: HttpRequest): LineageHeaders = {
    val path = httpRequest.uri.path.toString().split("/")

    LineageHeaders(
      extractHeaderOption(httpRequest, "Remote-Address"),
      path.take(path.length - 1).mkString("/"),
      bucketObject = path.lastOption.getOrElse("emptyObject"),
      httpRequest.method,
      httpRequest.entity.contentType,
      extractClient(extractHeaderOption(httpRequest, "User-Agent").getOrElse("generic")),
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
      method: String,
      contentType: ContentType,
      clientType: String,
      timestamp: Long): Future[LineagePostGuidResponse] = {

    def processIn(inputGUID: String, inputType: String) = List(guidRef(inputGUID, inputType))
    def processOut(outputGUID: String, outputType: String) = List(guidRef(outputGUID, outputType))

    for {
      serverGuid <- postData(serverEntities(userSTS, host, "staging_node").toJson).map(r => r.entityGUID)
      bucketGuid <- postData(bucketEntities(userSTS, bucket, "customer_PII").toJson).map(r => r.entityGUID)
      fileGuid <- postData(fileEntities(bucketGuid, bucketObject, userSTS, contentType, "customer_PII").toJson).map(r => r.entityGUID)
      processGuid <- method match {
        case access if access == "read" =>
          postData(
            processEntities(
              serverGuid, bucketGuid, fileGuid, userSTS, method, processIn(bucketGuid, "Bucket"), processOut(fileGuid, "DataFile"), clientType, timestamp
            ).toJson)
            .map(r => r.entityGUID)
        case access if access == "write" =>
          postData(
            processEntities(
              serverGuid, bucketGuid, fileGuid, userSTS, method, processIn(fileGuid, "DataFile"), processOut(bucketGuid, "Bucket"), clientType, timestamp
            ).toJson)
            .map(r => r.entityGUID)
      }
    } yield LineagePostGuidResponse(serverGuid, bucketGuid, fileGuid, processGuid)
  }
}
