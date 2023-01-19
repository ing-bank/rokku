package com.ing.wbaa.rokku.proxy.handler.namespace

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
          super.executeRequest(request, npaRequest, userSTS, s3request)
        case None =>
          throw new RokkuNamespaceBucketNotFoundException("no namespace for bucket")
      }
    } else {
      super.executeRequest(request, userSTS, s3request)
    }
  }
}
