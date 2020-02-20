package com.ing.wbaa.rokku.proxy.api.directive

import java.net.InetAddress

import akka.http.scaladsl.model.headers.{ RawHeader, _ }
import akka.http.scaladsl.model.{ HttpRequest, RemoteAddress }
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.ing.wbaa.rokku.proxy.data.AwsAccessKey
import org.scalatest.PrivateMethodTester
import org.scalatest.diagrams.Diagrams
import org.scalatest.wordspec.AnyWordSpec

class ProxyDirectivesSpec extends AnyWordSpec with ScalatestRouteTest with Diagrams with PrivateMethodTester {

  "Proxy Directives" should {

    val extractAuthorizationS3 = PrivateMethod[Option[AwsAccessKey]]('extractAuthorizationS3)

    "extract the accesskey from an authorization header" that {
      "uses AWS4-HMAC-SHA256 signer type" in {
        val header: RawHeader = RawHeader(
          "authorization",
          "AWS4-HMAC-SHA256 " +
            "Credential=testAccessKey/20180803/us-west-2/sts/aws4_request, " +
            "SignedHeaders=amz-sdk-invocation-id;amz-sdk-retry;host;user-agent;x-amz-date, " +
            "Signature=e435f68f5ca27ef0c02aba6df95c5cdc7560f0567b06824938f47710ca0ed2db"
        )

        val result = ProxyDirectives invokePrivate extractAuthorizationS3(header)
        assert(result.contains(AwsAccessKey("testAccessKey")))
      }

      "uses S3SignerType signer type" in {
        val header: RawHeader = RawHeader(
          "authorization",
          "AWS testAccessKey:RQcTmduqmXRc3EWHGWhgMvpZ1dY="
        )

        val result = ProxyDirectives invokePrivate extractAuthorizationS3(header)
        assert(result.contains(AwsAccessKey("testAccessKey")))
      }

      "returns None on unknown signerTypes" in {
        val header: RawHeader = RawHeader(
          "authorization",
          "NEWSIGNERTYPE this:is=not supported"
        )

        val result = ProxyDirectives invokePrivate extractAuthorizationS3(header)
        assert(result.isEmpty)
      }

      "returns None if no authorization header is present" in {
        val header: RawHeader = RawHeader(
          "SOMETHINGCOMPLETELYDIFFERENT",
          "NEWSIGNERTYPE this:is=not supported"
        )

        val result = ProxyDirectives invokePrivate extractAuthorizationS3(header)
        assert(result.isEmpty)
      }
    }

    "add forward headers to request" that {
      val requestWithRemoteIP = HttpRequest().withHeaders(RawHeader("Remote-Address", "1.2.3.4"))
      val requestWithRemoteAndFW = HttpRequest().withHeaders(RawHeader("Remote-Address", "1.2.3.4"), RawHeader("X-Forwarded-For", "3.4.5.6"))
      val requestWithNoXFW = HttpRequest().withHeaders(Nil)
      def testRoute(headerToReturn: String, request: HttpRequest) =
        ProxyDirectives.updateHeadersForRequest(request) { h =>
          complete(h.getHeader(headerToReturn).map[String](_.value).orElse("unknown"))
        }

      "return correct forward protocol" in {
        HttpRequest() ~> testRoute("X-Forwarded-Proto", requestWithRemoteAndFW) ~> check {
          val response = responseAs[String]
          assert(response == "HTTP/1.1")
        }
      }

      "add forward address unknown when remote-address not present" in {
        HttpRequest() ~> testRoute("X-Forwarded-For", requestWithNoXFW) ~> check {
          val response = responseAs[String]
          assert(response == "unknown")
        }
      }

      "add forward address with empty forwarded-for" in {
        requestWithRemoteIP ~> testRoute("X-Forwarded-For", requestWithRemoteIP) ~> check {
          val response = responseAs[String]
          assert(response == "1.2.3.4")
        }
      }

      "add forward address with filled forwarded-for" in {
        requestWithRemoteAndFW ~>
          testRoute("X-Forwarded-For", requestWithRemoteAndFW) ~> check {
            val response = responseAs[String]
            assert(response == "3.4.5.6, 1.2.3.4")
          }
      }
    }

    "extract s3Request" that {
      def testClientIp = {
        ProxyDirectives.extracts3Request { s3Request =>
          complete(s"Client ip = ${s3Request.clientIPAddress.toOption.map(_.getHostAddress).getOrElse("unknown")}, " +
            s"Header ips = ${s3Request.headerIPs.toString}")
        }
      }

      val authHeader = RawHeader(
        "authorization",
        "AWS testAccessKey:RQcTmduqmXRc3EWHGWhgMvpZ1dY="
      )

      "return unknown client ip and None header ips" in {
        HttpRequest().withHeaders(authHeader) ~> testClientIp ~> check {
          val response = responseAs[String]
          assert(response == "Client ip = unknown, Header ips = HeaderIPs(None,None,None)")
        }
      }

      "return 1.2.3.4 client ip and None header ips" in {
        HttpRequest().withHeaders(authHeader, `Remote-Address`(RemoteAddress(InetAddress.getByName("1.2.3.4")))) ~> testClientIp ~> check {
          val response = responseAs[String]
          assert(response == "Client ip = 1.2.3.4, Header ips = HeaderIPs(None,None,None)")
        }
      }

      "return 1.2.3.4 client ip and HeaderIPs(None,None,Some(2.3.4.5) header ips" in {
        HttpRequest().withHeaders(authHeader, `Remote-Address`(RemoteAddress(InetAddress.getByName("1.2.3.4"))),
          RawHeader("Remote-Address", "2.3.4.5")) ~> testClientIp ~> check {
            val response = responseAs[String]
            assert(response == "Client ip = 1.2.3.4, Header ips = HeaderIPs(None,None,Some(2.3.4.5))")
          }
      }

      "return 1.2.3.4 client ip and HeaderIPs(None,Some(ArraySeq(3.4.5.6)),Some(2.3.4.5) header ips" in {
        HttpRequest().withHeaders(authHeader, `Remote-Address`(RemoteAddress(InetAddress.getByName("1.2.3.4"))),
          RawHeader("Remote-Address", "2.3.4.5"),
          RawHeader("X-Forwarded-For", "3.4.5.6")) ~> testClientIp ~> check {
            val response = responseAs[String]
            assert(response == "Client ip = 1.2.3.4, Header ips = HeaderIPs(None,Some(ArraySeq(3.4.5.6)),Some(2.3.4.5))")
          }
      }

      "return 1.2.3.4 client ip and HeaderIPs(Some(4.5.6.7),Some(ArraySeq(3.4.5.6)),Some(2.3.4.5) header ips" in {
        HttpRequest().withHeaders(authHeader, `Remote-Address`(RemoteAddress(InetAddress.getByName("1.2.3.4"))),
          RawHeader("Remote-Address", "2.3.4.5"),
          RawHeader("X-Forwarded-For", "3.4.5.6"),
          RawHeader("X-Real-IP", "4.5.6.7")) ~> testClientIp ~> check {
            val response = responseAs[String]
            assert(response == "Client ip = 1.2.3.4, Header ips = HeaderIPs(Some(4.5.6.7),Some(ArraySeq(3.4.5.6)),Some(2.3.4.5))")
          }
      }
    }
  }
}
