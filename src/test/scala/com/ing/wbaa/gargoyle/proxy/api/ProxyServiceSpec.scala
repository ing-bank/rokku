package com.ing.wbaa.gargoyle.proxy.api

import java.net.InetAddress

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri.Authority
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.ing.wbaa.gargoyle.proxy.data._
import org.scalatest.{ DiagrammedAssertions, FlatSpec }

import scala.concurrent.Future

class ProxyServiceSpec extends FlatSpec with DiagrammedAssertions with ScalatestRouteTest {

  private trait ProxyServiceMock extends ProxyService {
    override implicit def system: ActorSystem = ActorSystem.create("test-system")

    override def validateRequest(request: HttpRequest, secretKey: String): Boolean = true
    override def executeRequest(request: HttpRequest, clientAddress: RemoteAddress): Future[HttpResponse] =
      Future(HttpResponse(entity =
        s"sendToS3: ${clientAddress.toOption.map(_.getHostName).getOrElse("unknown")}:${clientAddress.getPort()}"
      ))
    override def getUserForAccessKey(accessKey: AwsAccessKey): Future[Option[User]] = Future(
      Some(User("okUser", "okSecretKey", Set("okGroup"), "arn"))
    )
    override def areCredentialsAuthentic(awsRequestCredential: AwsRequestCredential): Future[Boolean] = Future(true)
    override def isUserAuthorizedForRequest(request: S3Request, user: User): Boolean = true
  }

  private def testRequest(accessKey: String = "okAccessKey") = HttpRequest(
    headers = List(
      RawHeader("authorization", s"AWS $accessKey:bla"),
      RawHeader("x-amz-security-token", "okSessionToken"),
      `Remote-Address`(RemoteAddress(InetAddress.getByName("6.7.8.9"), Some(1234)))
    ),
    uri = Uri(
      scheme = "http",
      authority = Authority(Uri.Host("host"), 3456),
      path = Uri.Path("/okBucket"))
  )

  "A proxy service" should "Successfully execute a request" in {
    testRequest() ~> new ProxyServiceMock {}.proxyServiceRoute ~> check {
      assert(status == StatusCodes.OK)
      val response = responseAs[String]
      assert(response == "sendToS3: 6.7.8.9:1234")
    }
  }

  it should "return a rejection when the user credentials cannot be authenticated" in {
    testRequest("notOkAccessKey") ~> new ProxyServiceMock {
      override def areCredentialsAuthentic(awsRequestCredential: AwsRequestCredential): Future[Boolean] = Future(false)
    }.proxyServiceRoute ~> check {
      assert(status == StatusCodes.Forbidden)
      val response = responseAs[String]
      assert(response == "Request not authenticated: " +
        "S3Request(" +
        "AwsRequestCredential(AwsAccessKey(notOkAccessKey),Some(AwsSessionToken(okSessionToken)))," +
        "Some(okBucket)," +
        "Read)")
    }
  }

  it should "return a rejection when user couldn't be found" in {
    testRequest() ~> new ProxyServiceMock {
      override def getUserForAccessKey(accessKey: AwsAccessKey): Future[Option[User]] = Future(None)
    }.proxyServiceRoute ~> check {
      assert(status == StatusCodes.Unauthorized)
      val response = responseAs[String]
      assert(response == "User not found for accesskey: okAccessKey")
    }
  }

  it should "return a rejection when an exception occurs in getting the user" in {
    testRequest() ~> new ProxyServiceMock {
      override def getUserForAccessKey(accessKey: AwsAccessKey): Future[Option[User]] = Future(throw new Exception("BOOM"))
    }.proxyServiceRoute ~> check {
      assert(status == StatusCodes.InternalServerError)
      val response = responseAs[String]
      assert(response == "There was an internal server error.")
    }
  }

  it should "return a rejection when an exception occurs in authentication" in {
    testRequest() ~> new ProxyServiceMock {
      override def areCredentialsAuthentic(awsRequestCredential: AwsRequestCredential): Future[Boolean] = Future(throw new Exception("BOOM"))
    }.proxyServiceRoute ~> check {
      assert(status == StatusCodes.InternalServerError)
      val response = responseAs[String]
      assert(response == "There was an internal server error.")
    }
  }

  it should "return a rejection when the request could not be validated" in {
    testRequest() ~> new ProxyServiceMock {
      override def validateRequest(request: HttpRequest, secretKey: String): Boolean = false
    }.proxyServiceRoute ~> check {
      assert(status == StatusCodes.BadRequest)
      val response = responseAs[String]
      val expectedResponse = s"Request could not be validated: ${testRequest()}"
      assert(response == expectedResponse)
    }
  }

  it should "return a rejection when user is not authorized" in {
    testRequest() ~> new ProxyServiceMock {
      override def isUserAuthorizedForRequest(request: S3Request, user: User): Boolean = false
    }.proxyServiceRoute ~> check {
      assert(status == StatusCodes.Unauthorized)
      val response = responseAs[String]
      assert(response == s"User (okUser) not authorized for request: " +
        s"S3Request(" +
        s"AwsRequestCredential(AwsAccessKey(okAccessKey),Some(AwsSessionToken(okSessionToken)))," +
        s"Some(okBucket)," +
        s"Read)")
    }
  }
}
