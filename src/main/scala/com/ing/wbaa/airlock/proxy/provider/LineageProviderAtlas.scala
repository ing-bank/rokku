package com.ing.wbaa.airlock.proxy.provider

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{ HttpMethods, HttpRequest, RemoteAddress }
import akka.stream.Materializer
import com.ing.wbaa.airlock.proxy.config.AtlasSettings
import com.ing.wbaa.airlock.proxy.data._
import com.ing.wbaa.airlock.proxy.provider.LineageProviderAtlas.LineageProviderAtlasException
import com.ing.wbaa.airlock.proxy.provider.atlas.{ LineageHelpers, RestClient }
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{ ExecutionContext, Future }

trait LineageProviderAtlas extends LazyLogging with RestClient with LineageHelpers {

  protected[this] implicit def system: ActorSystem
  protected[this] implicit def executionContext: ExecutionContext
  protected[this] implicit def materializer: Materializer
  protected[this] implicit def atlasSettings: AtlasSettings

  def createLineageFromRequest(httpRequest: HttpRequest, userSTS: User, clientIPAddress: RemoteAddress): Future[LineageResponse] = {
    val lineageHeaders = getLineageHeaders(httpRequest)
    val bucketObject = lineageHeaders.bucketObject.getOrElse("emptyObject")

    // we only report lineage for object operations. We do not track bucket create / delete etc.
    if (lineageHeaders.bucket.length > 1 && bucketObject != "emptyObject") {
      lineageHeaders.method match {
        // get object
        case HttpMethods.GET if lineageHeaders.queryParams.isEmpty || lineageHeaders.queryParams.contains("encoding-type") =>
          val externalObject = s"external_object_out/${bucketObject.split("/").takeRight(1).mkString}"
          readOrWriteLineage(lineageHeaders, userSTS, Read, clientIPAddress, Some(externalObject))

        // put object from outside of ceph
        case HttpMethods.PUT if lineageHeaders.queryParams.isEmpty && lineageHeaders.copySource.isEmpty =>
          val externalObject = s"external_object_in/${bucketObject.split("/").takeRight(1).mkString}"
          readOrWriteLineage(lineageHeaders, userSTS, Write, clientIPAddress, Some(externalObject))

        // put object - copy
        // if contains header x-amz-copy-source
        case HttpMethods.PUT if lineageHeaders.copySource.getOrElse("").length > 0 => lineageForCopyOperation(lineageHeaders, userSTS, Write, clientIPAddress)

        // post object (complete multipart)
        // aws request eg. POST /ObjectName?uploadId=UploadId and content-type application/xml
        case HttpMethods.POST if lineageHeaders.queryParams.getOrElse("").contains("uploadId") => readOrWriteLineage(lineageHeaders, userSTS, Write, clientIPAddress)

        // delete object
        case HttpMethods.DELETE if lineageHeaders.queryParams.isEmpty => deleteLineage(lineageHeaders)

        // delete on abort multipart
        // DELETE /ObjectName?uploadId=UploadId
        case HttpMethods.DELETE if lineageHeaders.queryParams.getOrElse("").contains("uploadId") => deleteLineage(lineageHeaders)

        case _ => Future.failed(LineageProviderAtlasException("Create lineage failed"))
      }
    } else {
      Future.failed(LineageProviderAtlasException("Create lineage failed"))
    }
  }
}

object LineageProviderAtlas {

  final case class LineageProviderAtlasException(private val message: String, private val cause: Throwable = None.orNull)
    extends Exception(message, cause)

}
