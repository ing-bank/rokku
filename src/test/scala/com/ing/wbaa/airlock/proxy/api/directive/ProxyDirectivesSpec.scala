package com.ing.wbaa.airlock.proxy.api.directive

import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.ing.wbaa.airlock.proxy.data.AwsAccessKey
import org.scalatest.{ DiagrammedAssertions, PrivateMethodTester, WordSpec }

class ProxyDirectivesSpec extends WordSpec with ScalatestRouteTest with DiagrammedAssertions with PrivateMethodTester {

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
  }

}
