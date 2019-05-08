package com.ing.wbaa.rokku.proxy.provider

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, MediaTypes, RemoteAddress}
import akka.stream.ActorMaterializer
import com.ing.wbaa.rokku.proxy.data.LineageLiterals._
import com.ing.wbaa.rokku.proxy.handler.FilterRecursiveMultiDelete.exctractMultideleteObjectsFlow
import com.ing.wbaa.rokku.proxy.provider.atlas.ModelKafka.bucketEntity
import com.ing.wbaa.rokku.proxy.data.{Read, RequestId, User, Write}
import com.ing.wbaa.rokku.proxy.provider.atlas.LineageHelpers

import scala.concurrent.Future

trait LineageProviderAtlas extends LineageHelpers {

  implicit protected[this] def system: ActorSystem

  def createLineageFromRequest(httpRequest: HttpRequest, userSTS: User, clientIPAddress: RemoteAddress)(
    implicit id: RequestId
  ): Future[Done] = {
    val lineageHeaders = getLineageHeaders(httpRequest)
    val bucketObject = lineageHeaders.bucketObject.getOrElse("emptyObject")
    val isMultideletePost =
      (httpRequest.entity.contentType.mediaType == MediaTypes.`application/xml` || httpRequest.entity.contentType.mediaType == MediaTypes.`application/octet-stream`) &&
        lineageHeaders.queryParams.getOrElse("") == "delete"

    if (lineageHeaders.bucket.length > 1) {
      lineageHeaders.method match {
        // mb bucket
        case HttpMethods.PUT if !lineageHeaders.bucket.isEmpty && bucketObject == "emptyObject" =>
          createSingleEntity(
            lineageHeaders.bucket,
            userSTS,
            bucketEntity(lineageHeaders.bucket, userSTS.userName.value, System.nanoTime())
          )

        // rm bucket
        case HttpMethods.DELETE if !lineageHeaders.bucket.isEmpty && bucketObject == "emptyObject" =>
          deleteEntityLineage(lineageHeaders.bucket, userSTS, AWS_S3_BUCKET_TYPE)
          deleteEntityLineage(s"${lineageHeaders.bucket}/", userSTS, AWS_S3_PSEUDO_DIR_TYPE) // we also have to remove pseudodir root

        // get object
        case HttpMethods.GET
            if lineageHeaders.queryParams.isEmpty || lineageHeaders.queryParams.contains("encoding-type") && !bucketObject.isEmpty =>
          val externalObject = s"$EXTERNAL_OBJECT_OUT/${bucketObject.split("/").takeRight(1).mkString}"
          readOrWriteLineage(lineageHeaders, userSTS, Read(), clientIPAddress, Some(externalObject))

        // put object from outside of ceph
        case HttpMethods.PUT
            if lineageHeaders.queryParams.isEmpty && lineageHeaders.copySource.isEmpty && !bucketObject.isEmpty =>
          val externalObject = s"$EXTERNAL_OBJECT_IN/${bucketObject.split("/").takeRight(1).mkString}"
          readOrWriteLineage(lineageHeaders, userSTS, Write(), clientIPAddress, Some(externalObject))

        // put object - copy
        // if contains header x-amz-copy-source
        case HttpMethods.PUT if lineageHeaders.copySource.getOrElse("").length > 0 && !bucketObject.isEmpty =>
          lineageForCopyOperation(lineageHeaders, userSTS, Write(), clientIPAddress)

        // multidelete by POST
        case HttpMethods.POST if isMultideletePost =>
          exctractMultideleteObjectsFlow(httpRequest.entity.dataBytes)(ActorMaterializer()).map { objects =>
            objects.map(o => deleteEntityLineage(s"${lineageHeaders.bucket}/$o", userSTS, AWS_S3_OBJECT_TYPE))
          }
          Future(Done)

        // post object (complete multipart)
        // aws request eg. POST /ObjectName?uploadId=UploadId and content-type application/xml
        case HttpMethods.POST
            if lineageHeaders.queryParams.getOrElse("").contains("uploadId") && !bucketObject.isEmpty =>
          val externalObject = s"$EXTERNAL_OBJECT_IN/${bucketObject.split("/").takeRight(1).mkString}"
          readOrWriteLineage(lineageHeaders, userSTS, Write(), clientIPAddress, Some(externalObject))

        // delete object
        case HttpMethods.DELETE if lineageHeaders.queryParams.isEmpty && !bucketObject.isEmpty =>
          deleteEntityLineage(lineageHeaders.bucketObject.getOrElse(""), userSTS)

        // delete on abort multipart
        // DELETE /ObjectName?uploadId=UploadId
        case HttpMethods.DELETE
            if lineageHeaders.queryParams.getOrElse("").contains("uploadId") && !bucketObject.isEmpty =>
          deleteEntityLineage(lineageHeaders.bucketObject.getOrElse(""), userSTS)

        case _ => Future.successful(Done)
      }
    } else {
      Future.successful(Done)
    }
  }
}
