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

  // for all filter only on wanted subresource

  // put object - copy
  // post object (complete multipart)
  // delete on abort multipart

  def createLineageFromRequest(httpRequest: HttpRequest, userSTS: User): Future[LineagePostGuidResponse] = {

    val timestamp = System.currentTimeMillis()
    val userName = userSTS.userName.value
    val lh = getLineageHeaders(httpRequest)

    if (lh.bucket.length > 1 && lh.bucketObject != "emptyObject") {
      logger.debug(s"Creating lineage for request ${lh.method.value} file ${lh.bucketObject} in ${lh.bucket} at ${timestamp}")
      lh.method match {
        // get object
        case HttpMethods.GET =>
          logger.debug(s"Creating Read lineage for request to ${lh.method.value} file ${lh.bucketObject} to ${lh.bucket} at ${timestamp}")
          postEnities(userName, lh.host, lh.bucket, lh.bucketObject, "read", lh.contentType, lh.clientType, timestamp)

        // put object
        case HttpMethods.POST | HttpMethods.PUT =>
          logger.debug(s"Creating Write lineage for request to ${lh.method.value} file ${lh.bucketObject} to ${lh.bucket} at ${timestamp}")
          postEnities(userName, lh.host, lh.bucket, lh.bucketObject, "write", lh.contentType, lh.clientType, timestamp)

        // delete object
        case HttpMethods.DELETE =>
          logger.debug(s"Creating Delete lineage for request to ${lh.method} file ${lh.bucketObject} to ${lh.bucket} at ${timestamp}")
          deleteEntities("DataFile", lh.bucketObject)
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
