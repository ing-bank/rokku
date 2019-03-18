package com.ing.wbaa.airlock.proxy.api

import akka.http.scaladsl.marshallers.xml.ScalaXmlSupport
import akka.http.scaladsl.model.{ HttpRequest }
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.ing.wbaa.airlock.proxy.data.{ Read, S3Request, User }

import scala.xml.NodeSeq

/**
 * Because aws api allows only list own buckets and we want user to see all bucket in our system
 * there is a hack to list bucket by radosgw-admin request
 * The standard aws functionality are in the ProxyService trait
 */
trait ProxyServiceWithListAllBuckets extends ProxyService with ScalaXmlSupport {

  protected[this] def listAllBuckets: Seq[String]

  override protected[this] def processAuthorizedRequest(httpRequest: HttpRequest, s3Request: S3Request, userSTS: User): Route = {
    s3Request match {
      //only when list buckets is requested we show all buckets
      case S3Request(_, None, None, accessType, _, _, _) if accessType == Read =>
        complete(getListAllMyBucketsXml())
      case _ => super.processAuthorizedRequest(httpRequest, s3Request, userSTS)
    }
  }

  private def getListAllMyBucketsXml(user: String = "npa", createDate: String = "2018-01-01T00:00:00.000Z"): NodeSeq = {
    <ListAllMyBucketsResult>
      <Owner>
        <ID>{ user }</ID>
        <DisplayName>{ user }</DisplayName>
      </Owner>
      <Buckets>
        { for (bucketName <- listAllBuckets) yield <Bucket><Name>{ bucketName }</Name><CreationDate>{ createDate }</CreationDate></Bucket> }
      </Buckets>
    </ListAllMyBucketsResult>
  }
}
