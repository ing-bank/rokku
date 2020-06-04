package com.ing.wbaa.rokku.proxy.provider

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.model.{ HttpMethods, HttpRequest, MediaTypes }
import akka.stream.ActorMaterializer
import com.ing.wbaa.rokku.proxy.data.LineageLiterals._
import com.ing.wbaa.rokku.proxy.data._
import com.ing.wbaa.rokku.proxy.handler.FilterRecursiveMultiDelete.exctractMultideleteObjectsFlow
import com.ing.wbaa.rokku.proxy.handler.LoggerHandlerWithId
import com.ing.wbaa.rokku.proxy.provider.atlas.LineageHelpers
import com.ing.wbaa.rokku.proxy.provider.atlas.ModelKafka.createBucketEntity

import scala.concurrent.Future

trait LineageProviderAtlas extends LineageHelpers {

  protected[this] implicit def system: ActorSystem

  private val logger = new LoggerHandlerWithId

  private val whitelistUserAgents = system.settings.config.getString("rokku.atlas.whitelistUserAgentSplitByComma").trim.split(",")

  def createLineageFromRequest(httpRequest: HttpRequest, userSTS: User, userIPs: UserIps)(implicit id: RequestId): Future[Done] = {
    val lineageHeaders = getLineageHeaders(httpRequest)
    val pseudoDir = lineageHeaders.pseduoDir
    val bucketObject = lineageHeaders.bucketObject
    val isMultideletePost =
      (httpRequest.entity.contentType.mediaType == MediaTypes.`application/xml` || httpRequest.entity.contentType.mediaType == MediaTypes.`application/octet-stream`) &&
        lineageHeaders.queryParams.getOrElse("") == "delete"

    val extractObjectFromPath = bucketObject.getOrElse("").split("/").takeRight(1).mkString

    val isClientTypeWhitelisted = whitelistUserAgents.contains(lineageHeaders.clientType.getOrElse("").toLowerCase())

    if (lineageHeaders.bucket.length > 1 && isClientTypeWhitelisted) {
      lineageHeaders.method match {
        // mb bucket
        case HttpMethods.PUT if !lineageHeaders.bucket.isEmpty && pseudoDir.isEmpty && bucketObject.isEmpty =>
          createSingleEntity(lineageHeaders.bucket, userSTS, createBucketEntity(lineageHeaders.bucket, userSTS.userName.value, System.nanoTime(), lineageHeaders.classifications.getOrElse(BucketClassification(), List.empty)))

        // rm bucket
        case HttpMethods.DELETE if !lineageHeaders.bucket.isEmpty && pseudoDir.isEmpty && bucketObject.isEmpty =>
          deleteEntityLineage(lineageHeaders.bucket, userSTS, AWS_S3_BUCKET_TYPE)

        // get object
        case HttpMethods.GET if lineageHeaders.queryParams.isEmpty || lineageHeaders.queryParams.contains("encoding-type") && bucketObject.isDefined =>
          val externalObject = s"$EXTERNAL_OBJECT_OUT/$extractObjectFromPath"
          readOrWriteLineage(lineageHeaders, userSTS, Read(), userIPs, Some(externalObject))

        // put object from outside of ceph
        case HttpMethods.PUT if lineageHeaders.queryParams.isEmpty && lineageHeaders.copySource.isEmpty && (pseudoDir.isDefined || bucketObject.isDefined) =>
          val externalObject = s"$EXTERNAL_OBJECT_IN/${bucketObject.getOrElse(pseudoDir.get).split("/").takeRight(1).mkString}"
          readOrWriteLineage(lineageHeaders, userSTS, Put(), userIPs, Some(externalObject))

        // put object - copy
        // if contains header x-amz-copy-source
        case HttpMethods.PUT if lineageHeaders.copySource.getOrElse("").length > 0 && (pseudoDir.isDefined || bucketObject.isDefined) =>
          lineageForCopyOperation(lineageHeaders, userSTS, Put(), userIPs)

        // multidelete by POST
        case HttpMethods.POST if isMultideletePost =>
          exctractMultideleteObjectsFlow(httpRequest.entity.dataBytes)(ActorMaterializer()).map { objects =>
            objects.map(o => deleteEntityLineage(s"${lineageHeaders.bucket}/$o", userSTS, AWS_S3_OBJECT_TYPE))
          }
          Future(Done)

        // post object (complete multipart)
        // aws request eg. POST /ObjectName?uploadId=UploadId and content-type application/xml
        case HttpMethods.POST if lineageHeaders.queryParams.getOrElse("").contains("uploadId") && bucketObject.isDefined =>
          val externalObject = s"$EXTERNAL_OBJECT_IN/$extractObjectFromPath"
          readOrWriteLineage(lineageHeaders, userSTS, Post(), userIPs, Some(externalObject))

        // delete object
        case HttpMethods.DELETE if lineageHeaders.queryParams.isEmpty && (pseudoDir.isDefined || bucketObject.isDefined) =>
          deleteEntityLineage(lineageHeaders.bucketObject.getOrElse(pseudoDir.getOrElse("")), userSTS, bucketObject.map(_ => AWS_S3_OBJECT_TYPE).getOrElse(AWS_S3_PSEUDO_DIR_TYPE))

        // delete on abort multipart
        // DELETE /ObjectName?uploadId=UploadId
        case HttpMethods.DELETE if lineageHeaders.queryParams.getOrElse("").contains("uploadId") && bucketObject.isDefined =>
          deleteEntityLineage(lineageHeaders.bucketObject.getOrElse(""), userSTS)

        case _ =>
          logger.debug("no case for lineage {}", httpRequest)
          Future.successful(Done)
      }
    } else {
      Future.successful(Done)
    }
  }
}
