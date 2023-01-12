package com.ing.wbaa.rokku.proxy.handler.namespace

import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse }
import com.ing.wbaa.rokku.proxy.data.{ RequestId, S3Request, User }
import com.ing.wbaa.rokku.proxy.handler.RequestHandlerS3
import com.ing.wbaa.rokku.proxy.handler.exception.RokkuNamespaceBucketNotFoundException

import scala.concurrent.Future

trait RequestHandlerS3WithNamespaces extends RequestHandlerS3 with NamespacesHandler {

  override protected[this] def executeRequest(request: HttpRequest, userSTS: User, s3request: S3Request)(implicit id: RequestId): Future[HttpResponse] = {

    if (namespaceSettings.isEnabled) {
      bucketNamespaceCredentials(request) match {
        case Some(namespaceCredentials) =>
          val npaRequest = getNpaRequest(request, namespaceCredentials)
          val userAgent = request.getHeader("User-Agent").orElse(RawHeader("User-Agent", "unknown")).value()

          val newRequest = request
            .withUri(request.uri.withScheme(storageS3Settings.storageS3Schema).withAuthority(storageS3Settings.storageS3Authority))
            .withEntity(request.entity)
            .addHeader(RawHeader("User-Agent", userAgent))
            .removeHeader("Authorization")
            .addHeader(RawHeader("Authorization", npaRequest.getHeaders.get("Authorization")))

          fireRequestToS3(newRequest, userSTS).flatMap { response =>
            Future(filterResponse(request, userSTS, s3request, response))
          }
        case None =>
          throw new RokkuNamespaceBucketNotFoundException("no namespace for bucket")
      }
    } else {
      super.executeRequest(request, userSTS, s3request)
    }
  }
}
