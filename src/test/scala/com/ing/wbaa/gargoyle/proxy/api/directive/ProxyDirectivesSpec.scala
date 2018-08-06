//package com.ing.wbaa.gargoyle.proxy.api.directive
//
//import akka.http.scaladsl.model.HttpHeader
//import com.ing.wbaa.gargoyle.proxy.api.directive.ProxyDirectives.AuthorizationHeaderS3
//import org.scalatest.{DiagrammedAssertions, PrivateMethodTester, WordSpec}
//
//class ProxyDirectivesSpec extends WordSpec with DiagrammedAssertions {
//
//  "Proxy Directives" should {
//    "extract the accesskey from an authorization header with AWS4-HMAC-SHA256 type" in {
//      val h = HttpHeader.parse(
//        "authorization",
//        "authorization: AWS4-HMAC-SHA256 " +
//          "Credential=ewaefaef/20180803/us-west-2/sts/aws4_request, " +
//          "SignedHeaders=amz-sdk-invocation-id;amz-sdk-retry;host;user-agent;x-amz-date, " +
//          "Signature=e435f68f5ca27ef0c02aba6df95c5cdc7560f0567b06824938f47710ca0ed2db"
//      )
//
//      val result = ProxyDirectives invokePrivate extractAuthorizationS3(h)
//      println(result)
//    }
//  }
//
//}
