package com.ing.wbaa.rokku.proxy.api

import java.net.InetAddress

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri.Authority
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.stream.{ ActorMaterializer, Materializer }
import com.ing.wbaa.rokku.proxy.data._
import com.ing.wbaa.rokku.proxy.handler.parsers.RequestParser.{ AWSRequestType, RequestTypeUnknown }
import org.scalatest.{ DiagrammedAssertions, FlatSpec }

import scala.concurrent.{ ExecutionContext, Future }

class ProxyServiceWithListAllBucketsSpec extends FlatSpec with DiagrammedAssertions with ScalatestRouteTest {

  private trait ProxyServiceMock extends ProxyServiceWithListAllBuckets {
    override implicit def system: ActorSystem = ActorSystem.create("test-system")

    override implicit def executionContext: ExecutionContext = system.dispatcher

    implicit def materializer: Materializer = ActorMaterializer()

    override def executeRequest(request: HttpRequest, userSTS: User, s3request: S3Request)(implicit id: RequestId): Future[HttpResponse] =
      Future(HttpResponse(status = StatusCodes.OK))

    override def areCredentialsActive(awsRequestCredential: AwsRequestCredential)(implicit id: RequestId): Future[Option[User]] = Future(
      Some(User(UserName("okUser"), Set(UserGroup("okGroup")), AwsAccessKey("accesskey"), AwsSecretKey("secretkey")))
    )

    override def isUserAuthorizedForRequest(request: S3Request, user: User)(implicit id: RequestId): Boolean = true

    override def isUserAuthenticated(httpRequest: HttpRequest, awsSecretKey: AwsSecretKey)(implicit id: RequestId): Boolean = true

    override protected[this] def handlePostRequestActions(response: HttpResponse, httpRequest: HttpRequest, s3Request: S3Request, userSTS: User)(implicit id: RequestId): Unit = ()
    override protected[this] def listAllBuckets: Seq[String] = List("bucket1", "bucket2")
    override def auditLog(s3Request: S3Request, httpRequest: HttpRequest, user: String, awsRequest: AWSRequestType, responseStatus: StatusCode)(implicit id: RequestId): Future[Done] = Future(Done)

    override def awsRequestFromRequest(request: HttpRequest): AWSRequestType = RequestTypeUnknown()

    override val requestPersistenceEnabled: Boolean = false
    override val configuredPersistenceId: String = "localhost-1"
  }

  private def testRequest(accessKey: String = "okAccessKey", path: String = "/") = HttpRequest(
    headers = List(
      RawHeader("authorization", s"AWS $accessKey:bla"),
      RawHeader("x-amz-security-token", "okSessionToken"),
      `Remote-Address`(RemoteAddress(InetAddress.getByName("6.7.8.9"), Some(1234)))
    ),
    uri = Uri(
      scheme = "http",
      authority = Authority(Uri.Host("host"), 3456),
      path = Uri.Path(path))
  )

  val requestIdString = """\S{8}-\S{4}-\S{4}-\S{4}-\S{12}""".r

  "A proxy service" should "Successfully execute a request" in {
    testRequest() ~> new ProxyServiceMock {}.proxyServiceRoute ~> check {
      assert(status == StatusCodes.OK)
      assert(responseAs[String].replaceAll("\\s", "") == "<ListAllMyBucketsResult>" +
        "<Owner><ID>npa</ID><DisplayName>npa</DisplayName></Owner>" +
        "<Buckets>" + "" +
        "<Bucket><Name>bucket1</Name><CreationDate>2018-01-01T00:00:00.000Z</CreationDate></Bucket>" +
        "<Bucket><Name>bucket2</Name><CreationDate>2018-01-01T00:00:00.000Z</CreationDate></Bucket>" +
        "</Buckets>" +
        "</ListAllMyBucketsResult>")
    }
  }

  it should "return an accessDenied when the user credentials cannot be authenticated" in {
    testRequest("notOkAccessKey") ~> new ProxyServiceMock {
      override def areCredentialsActive(awsRequestCredential: AwsRequestCredential)(implicit id: RequestId): Future[Option[User]] = Future(None)
    }.proxyServiceRoute ~> check {
      assert(status == StatusCodes.Forbidden)
      val response = requestIdString.replaceAllIn(responseAs[String].replaceAll("\\s", ""), "")
      assert(response == "<Error><Code>AccessDenied</Code><Message>AccessDenied</Message><Resource></Resource><RequestId></RequestId></Error>")
    }
  }

  it should "return a serviceUnavailable when an exception occurs in authentication" in {
    testRequest() ~> new ProxyServiceMock {
      override def areCredentialsActive(awsRequestCredential: AwsRequestCredential)(implicit id: RequestId): Future[Option[User]] = Future(throw new Exception("BOOM"))
    }.proxyServiceRoute ~> check {
      assert(status == StatusCodes.InternalServerError)
      val response = requestIdString.replaceAllIn(responseAs[String].replaceAll("\\s", ""), "")
      assert(response == "<Error><Code>InternalServerError</Code><Message>InternalServerError</Message><Resource></Resource><RequestId></RequestId></Error>")
    }
  }

  it should "return an Unauthorized when user is not authorized" in {
    testRequest() ~> new ProxyServiceMock {
      override def isUserAuthorizedForRequest(request: S3Request, user: User)(implicit id: RequestId): Boolean = false
    }.proxyServiceRoute ~> check {
      assert(status == StatusCodes.Unauthorized)
      val response = requestIdString.replaceAllIn(responseAs[String].replaceAll("\\s", ""), "")
      assert(response == "<Error><Code>Unauthorized</Code><Message>Unauthorized</Message><Resource></Resource><RequestId></RequestId></Error>")
    }
  }

  it should "return an accessDenied when user is not authenticated" in {
    testRequest() ~> new ProxyServiceMock {
      override def isUserAuthenticated(httpRequest: HttpRequest, awsSecretKey: AwsSecretKey)(implicit id: RequestId): Boolean = false
    }.proxyServiceRoute ~> check {
      assert(status == StatusCodes.Forbidden)
      val response = requestIdString.replaceAllIn(responseAs[String].replaceAll("\\s", ""), "")
      assert(response == "<Error><Code>AccessDenied</Code><Message>AccessDenied</Message><Resource></Resource><RequestId></RequestId></Error>")
    }
  }
}
