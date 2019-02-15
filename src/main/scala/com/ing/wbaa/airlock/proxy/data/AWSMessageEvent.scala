package com.ing.wbaa.airlock.proxy.data

import java.time.Instant

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{ HttpMethod, RemoteAddress }
import com.ing.wbaa.airlock.proxy.provider.aws.S3ObjectAction
import spray.json.DefaultJsonProtocol

case class Records(records: List[AWSMessageEvent])

case class UserIdentity(principalId: String)

case class RequestParameters(sourceIPAddress: String)

case class ResponseElements(`x-amz-request-id`: String, `x-amz-id-2`: String)

case class OwnerIdentity(principalId: String)

case class BucketProps(name: String, ownerIdentity: OwnerIdentity, arn: String)

case class ObjectProps(key: String, size: Int, eTag: String, versionId: String, sequencer: String)

case class S3(s3SchemaVersion: String, configurationId: String, bucket: BucketProps, `object`: ObjectProps)

case class AWSMessageEvent(
    eventVersion: String,
    eventSource: String,
    awsRegion: String,
    eventTime: String,
    eventName: String,
    userIdentity: UserIdentity,
    requestParameters: RequestParameters,
    responseElements: ResponseElements,
    s3: S3
)

trait AWSMessageEventJsonSupport extends SprayJsonSupport with DefaultJsonProtocol {

  implicit val ownerIdentityFormat = jsonFormat1(OwnerIdentity)
  implicit val bucketFormat = jsonFormat3(BucketProps)
  implicit val objectPropsFormat = jsonFormat5(ObjectProps)
  implicit val s3Format = jsonFormat4(S3)
  implicit val userIdentityFormat = jsonFormat1(UserIdentity)
  implicit val requestParametersFormat = jsonFormat1(RequestParameters)
  implicit val responseElementsFormat = jsonFormat2(ResponseElements)
  implicit val AWSMessageEventFormat = jsonFormat9(AWSMessageEvent)
  implicit val recordsFormat = jsonFormat1(Records)

  import spray.json._

  def prepareAWSMessage(s3Request: S3Request, method: HttpMethod, principalId: String, clientIPAddress: RemoteAddress, s3Action: S3ObjectAction): Option[JsValue] = {
    s3Request.s3BucketPath.flatMap { bucket =>
      s3Request.s3Object.map { s3object =>
        Records(List(AWSMessageEvent(
          "2.1",
          "airlock:s3",
          "us-east-1",
          Instant.now().toString(),
          s3Action.value,
          UserIdentity(principalId),
          RequestParameters(clientIPAddress.getAddress().toString),
          ResponseElements("", ""),
          S3("1.0", "", BucketProps(bucket, OwnerIdentity(""), ""),
            ObjectProps(s3object, 0, "", "", ""))))
        ).toJson
      }
    }
  }
}

