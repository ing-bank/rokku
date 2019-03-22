package com.ing.wbaa.airlock.proxy.provider

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.model.{ HttpMethods, HttpRequest, RemoteAddress }
import com.ing.wbaa.airlock.proxy.config.AtlasSettings
import com.ing.wbaa.airlock.proxy.data._
import com.ing.wbaa.airlock.proxy.provider.atlas.{ LineageHelpers, RestClient }

import scala.concurrent.Future

trait LineageProviderAtlas extends RestClient with LineageHelpers {

  protected[this] implicit def system: ActorSystem
  protected[this] implicit def atlasSettings: AtlasSettings

  def createLineageFromRequest(httpRequest: HttpRequest, userSTS: User, clientIPAddress: RemoteAddress)(implicit id: RequestId): Future[Done] = {
    val lineageHeaders = getLineageHeaders(httpRequest)
    val bucketObject = lineageHeaders.bucketObject.getOrElse("emptyObject")

    // we only report lineage for object operations. We do not track bucket create / delete etc.
    if (lineageHeaders.bucket.length > 1 && bucketObject != "emptyObject") {
      lineageHeaders.method match {
        //todo: add case for make / remove bucket

        // get object
        case HttpMethods.GET if lineageHeaders.queryParams.isEmpty || lineageHeaders.queryParams.contains("encoding-type") =>
          val externalObject = s"$EXTERNAL_OBJECT_OUT/${bucketObject.split("/").takeRight(1).mkString}"
          kafkaReadOrWriteLineage(lineageHeaders, userSTS, Read, clientIPAddress, Some(externalObject))

        // put object from outside of ceph
        case HttpMethods.PUT if lineageHeaders.queryParams.isEmpty && lineageHeaders.copySource.isEmpty =>
          val externalObject = s"$EXTERNAL_OBJECT_IN/${bucketObject.split("/").takeRight(1).mkString}"
          kafkaReadOrWriteLineage(lineageHeaders, userSTS, Write, clientIPAddress, Some(externalObject))

        // put object - copy
        // if contains header x-amz-copy-source
        case HttpMethods.PUT if lineageHeaders.copySource.getOrElse("").length > 0 => Future.successful(Done)

        // post object (complete multipart)
        // aws request eg. POST /ObjectName?uploadId=UploadId and content-type application/xml
        case HttpMethods.POST if lineageHeaders.queryParams.getOrElse("").contains("uploadId") => kafkaReadOrWriteLineage(lineageHeaders, userSTS, Write, clientIPAddress)

        // delete object
        case HttpMethods.DELETE if lineageHeaders.queryParams.isEmpty => Future.successful(Done)

        // delete on abort multipart
        // DELETE /ObjectName?uploadId=UploadId
        case HttpMethods.DELETE if lineageHeaders.queryParams.getOrElse("").contains("uploadId") => Future.successful(Done)

        case _ => Future.successful(Done)
      }
    } else {
      Future.successful(Done)
    }
  }
}

object LineageProviderAtlas {

  final case class LineageProviderAtlasException(private val message: String, private val cause: Throwable = None.orNull)
    extends Exception(message, cause)

}
