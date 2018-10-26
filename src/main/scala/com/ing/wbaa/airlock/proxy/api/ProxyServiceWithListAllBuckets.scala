package com.ing.wbaa.airlock.proxy.api

import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.server.Route
import com.ing.wbaa.airlock.proxy.data.{ Read, S3Request, User }
import akka.http.scaladsl.server.Directives._

/**
 * Because aws api allows only list own buckets and we want user to see all bucket in our system
 * there is a hack to list bucket by radosgw-admin request
 * The standard aws functionality are in the ProxyService trait
 */
trait ProxyServiceWithListAllBuckets extends ProxyService {

  protected[this] def listAllBuckets: Seq[String]

  override protected[this] def processAuthorizedRequest(httpRequest: HttpRequest, s3Request: S3Request, userSTS: User): Route = {
    s3Request match {
      //only when list buckets is requested we show all buckets
      case S3Request(_, None, None, accessType) if accessType == Read =>
        val r = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
          "<ListAllMyBucketsResult><Owner><ID>npa</ID><DisplayName>npa</DisplayName></Owner><Buckets>"
        val b = listAllBuckets
          .map(name => s"<Bucket><Name>$name</Name><CreationDate>2018-01-01T00:00:00.000Z</CreationDate></Bucket>").fold("")(_ + _)
        val e = "</Buckets></ListAllMyBucketsResult>"
        complete(r + b + e)
      case _ => super.processAuthorizedRequest(httpRequest, s3Request, userSTS)
    }
  }
}
