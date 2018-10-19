package com.ing.wbaa.airlock.proxy.provider.atlas

import akka.http.scaladsl.model.{ContentType, HttpRequest}
import com.ing.wbaa.airlock.proxy.data.{LineageHeaders, LineagePostGuidResponse}
import com.ing.wbaa.airlock.proxy.provider.atlas.Model._
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Future

trait LineageHelpers extends LazyLogging with RestClient {

  def serverEntities(userSTS: String, host: String, classification: String) =
    Entities(Seq(Server(
      "Server",
      userSTS,
      ServerAttributes(host, host, host, host),
      Seq(Classification(classification)))))

  def bucketEntities(userSTS: String, bucket: String, classification: String) =
    Entities(Seq(Bucket(
      "Bucket",
      userSTS,
      BucketAttributes(bucket, bucket, bucket),
      Seq(Classification(classification)))))

  def fileEntities(
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

  def processEntities(
                       serverGuid: String,
                       bucketGuid: String,
                       fileGuid: String,
                       userSTS: String,
                       method: String,
                       inputs: List[guidRef],
                       outputs: List[guidRef],
                       timestamp: Long) = {
    Entities(Seq(Ingestion(
      "airlock_client",
      userSTS,
      IngestionAttributes(s"aws_cli_${timestamp}", s"aws_cli_${timestamp}", method, userSTS,
        guidRef(serverGuid, "Server"),
        inputs,
        outputs))))
  }

  private def extractClient(userAgent: String) =
    """(\S+)/\S+""".r
      .findFirstMatchIn(userAgent)
      .map(_ group 1).getOrElse("")

  // createLineageFromRequest is used in RequestHandlerS3 where there is no notion of S3Request. It seems easier to
  // repeat httpRequest parsing here for needed values
  def getLineageHeaders(httpRequest: HttpRequest): LineageHeaders = {
    val host = httpRequest.getHeader("Remote-Address").get().value() // make option
    val path = httpRequest.uri.path.toString().split("/")
    val bucket = path.take(path.length - 1).mkString("/")
    val bucketObject = path.lastOption.getOrElse("emptyObject")
    val method = httpRequest.method
    val contentType = httpRequest.entity.contentType
    val clientType = extractClient(httpRequest.getHeader("User-Agent").get().value()) // make option

    LineageHeaders(host, bucket, bucketObject, method, contentType, clientType)
  }

  def splitQueryToJavaMap(queryString: String): Map[String, List[String]] =
    queryString.split("&").map { paramAndValue =>
      paramAndValue.split("=")
        .grouped(2)
        .map {
          case Array(k, v) => (k, List(v))
          case Array(k)    => (k, List(""))
        }
    }.toList.flatten.toMap

  // file entity delete
  // for now it is just deleting file entity and no related objects like eg. aws_cli_script, which uploaded or downloaded
  // file to bucket. Once we delete aws_cli_script object we will lose track of whats has been deleted
  // We need to come up with process of tracking file delete
  def deleteEntities(typeName: String, entityName: String): Future[LineagePostGuidResponse] =
    for {
      entityGuid <- getEntityGUID(typeName, entityName)
      _ <- deleteEntity(entityGuid)

    } yield LineagePostGuidResponse("", "", entityGuid, "")

  // move to LineageHelpers and split to postEntity
  def postEnities(userSTS: String,
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
          postData( //todo: is this true assumtion that bucket is input?
            processEntities(serverGuid, bucketGuid, fileGuid, userSTS, method, processIn(bucketGuid, "Bucket"), processOut(fileGuid, "DataFile"), timestamp).toJson)
            .map(r => r.entityGUID)
        case access if access == "write" =>
          postData(
            processEntities(serverGuid, bucketGuid, fileGuid, userSTS, method, processIn(fileGuid, "DataFile"), processOut(bucketGuid, "Bucket"), timestamp).toJson)
            .map(r => r.entityGUID)
      }
    } yield LineagePostGuidResponse(serverGuid, bucketGuid, fileGuid, processGuid)
  }

}
