package com.ing.wbaa.airlock.proxy.provider

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.model.{ HttpMethods, HttpRequest, RemoteAddress }
import com.ing.wbaa.airlock.proxy.config.AtlasSettings
import com.ing.wbaa.airlock.proxy.data._
import com.ing.wbaa.airlock.proxy.provider.atlas.LineageHelpers
import com.ing.wbaa.airlock.proxy.provider.atlas.ModelKafka.bucketEntity
import com.ing.wbaa.airlock.proxy.data.LineageLiterals._

import scala.concurrent.Future

trait LineageProviderAtlas extends LineageHelpers {

  protected[this] implicit def system: ActorSystem
  protected[this] implicit def atlasSettings: AtlasSettings

  def createLineageFromRequest(httpRequest: HttpRequest, userSTS: User, clientIPAddress: RemoteAddress)(implicit id: RequestId): Future[Done] = {
    val lineageHeaders = getLineageHeaders(httpRequest)
    val bucketObject = lineageHeaders.bucketObject.getOrElse("emptyObject")

    if (lineageHeaders.bucket.length > 1) {
      lineageHeaders.method match {
        // mb bucket
        case HttpMethods.PUT if !lineageHeaders.bucket.isEmpty && bucketObject == "emptyObject" =>
          createSingleEntity(lineageHeaders.bucket, userSTS, bucketEntity(lineageHeaders.bucket, userSTS.userName.value, newGuid), AWS_S3_BUCKET_TYPE)

        // rm bucket
        case HttpMethods.DELETE if !lineageHeaders.bucket.isEmpty && bucketObject == "emptyObject" =>
          deleteEntityLineage(lineageHeaders.bucket, userSTS, AWS_S3_BUCKET_TYPE)
          deleteEntityLineage(s"${lineageHeaders.bucket}/", userSTS, AWS_S3_PSEUDO_DIR_TYPE) // we also have to remove pseudodir root

        // get object
        case HttpMethods.GET if lineageHeaders.queryParams.isEmpty || lineageHeaders.queryParams.contains("encoding-type") && !bucketObject.isEmpty =>
          val externalObject = s"$EXTERNAL_OBJECT_OUT/${bucketObject.split("/").takeRight(1).mkString}"
          kafkaReadOrWriteLineage(lineageHeaders, userSTS, Read(), clientIPAddress, Some(externalObject))

        // put object from outside of ceph
        case HttpMethods.PUT if lineageHeaders.queryParams.isEmpty && lineageHeaders.copySource.isEmpty && !bucketObject.isEmpty =>
          val externalObject = s"$EXTERNAL_OBJECT_IN/${bucketObject.split("/").takeRight(1).mkString}"
          kafkaReadOrWriteLineage(lineageHeaders, userSTS, Write(), clientIPAddress, Some(externalObject))

        // put object - copy
        // if contains header x-amz-copy-source
        case HttpMethods.PUT if lineageHeaders.copySource.getOrElse("").length > 0 && !bucketObject.isEmpty =>
          lineageForCopyOperation(lineageHeaders, userSTS, Write(), clientIPAddress)

        // post object (complete multipart)
        // aws request eg. POST /ObjectName?uploadId=UploadId and content-type application/xml
        case HttpMethods.POST if lineageHeaders.queryParams.getOrElse("").contains("uploadId") && !bucketObject.isEmpty =>
          kafkaReadOrWriteLineage(lineageHeaders, userSTS, Write(), clientIPAddress)

        // delete object
        case HttpMethods.DELETE if lineageHeaders.queryParams.isEmpty && !bucketObject.isEmpty =>
          deleteEntityLineage(lineageHeaders.bucketObject.getOrElse(""), userSTS)

        // delete on abort multipart
        // DELETE /ObjectName?uploadId=UploadId
        case HttpMethods.DELETE if lineageHeaders.queryParams.getOrElse("").contains("uploadId") && !bucketObject.isEmpty =>
          deleteEntityLineage(lineageHeaders.bucketObject.getOrElse(""), userSTS)

        case _ => Future.successful(Done)
      }
    } else {
      Future.successful(Done)
    }
  }
}
