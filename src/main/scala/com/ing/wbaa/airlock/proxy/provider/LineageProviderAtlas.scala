package com.ing.wbaa.airlock.proxy.provider

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{ HttpMethods, HttpRequest }
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

  def createLineageFromRequest(httpRequest: HttpRequest, userSTS: User): Future[LineageResponse] = {

    val timestamp = System.currentTimeMillis()
    val userName = userSTS.userName.value
    val lh = getLineageHeaders(httpRequest)
    val host = lh.host.getOrElse("unknown")

    def readOrWriteLineage(method: String, bucket: String): Future[LineagePostGuidResponse] = {
      logger.debug(s"Creating $method lineage for request to ${lh.method.value} file ${lh.bucketObject} to $bucket at $timestamp")
      postEnities(userName, host, bucket, lh.bucketObject, method, lh.contentType, lh.clientType, timestamp)
    }

    def delLineage(method: String): Future[LineageGuidResponse] = {
      logger.debug(s"Creating Delete lineage for request to ${lh.method} file ${lh.bucketObject} to ${lh.bucket} at $timestamp")
      deleteEntities("DataFile", lh.bucketObject)
    }

    // we only report lineage for object operations. We do not track bucket create / delete etc.
    if (lh.bucket.length > 1 && lh.bucketObject != "emptyObject") {
      lh.method match {
        // get object
        case HttpMethods.GET if lh.queryParams.isEmpty || lh.queryParams.contains("encoding-type") =>
          readOrWriteLineage("read", lh.bucket)

        // put object
        case HttpMethods.PUT if lh.queryParams.isEmpty && lh.copySource.isEmpty => readOrWriteLineage("write", lh.bucket)

        // put object - copy
        // if contains header x-amz-copy-source
        case HttpMethods.PUT if lh.copySource.getOrElse("").length > 0 =>
          readOrWriteLineage("read", lh.copySource.get)
          readOrWriteLineage("write", lh.bucket)

        // post object (complete multipart)
        // aws request eg. POST /ObjectName?uploadId=UploadId and content-type application/xml
        case HttpMethods.POST if lh.queryParams.getOrElse("").contains("uploadId") => readOrWriteLineage("write", lh.bucket)

        // delete object
        case HttpMethods.DELETE if lh.queryParams.isEmpty => delLineage("delete")

        // delete on abort multipart
        // DELETE /ObjectName?uploadId=UploadId
        case HttpMethods.DELETE if lh.queryParams.getOrElse("").contains("uploadId") => delLineage("delete")

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
