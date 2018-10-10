package com.ing.wbaa.gargoyle.proxy.provider

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import com.ing.wbaa.gargoyle.proxy.data.AwsSecretKey
import org.scalatest.{ DiagrammedAssertions, WordSpec }

class SignatureProviderAwsSpec extends WordSpec with DiagrammedAssertions with SignatureProviderAws {

  def fakeIncomingHttpRequest(method: HttpMethod, path: String, headers: List[HttpHeader], queryString: String = "", destPort: Int = 8987): HttpRequest = {

    val uri = Uri(
      scheme = "http",
      authority = Uri.Authority(host = Uri.Host("127.0.0.1"), port = destPort)).withPath(Uri.Path(path)).withRawQueryString(queryString)

    method match {
      case HttpMethods.GET                    => HttpRequest(method, uri, headers, HttpEntity(ContentTypes.`application/octet-stream`, Array[Byte]()))
      case HttpMethods.POST | HttpMethods.PUT => HttpRequest(method, uri, headers, HttpEntity(ContentTypes.`application/json`, "{}"))
      case HttpMethods.DELETE                 => HttpRequest(method, uri, headers)
      case _                                  => HttpRequest(method, uri, Nil)
    }
  }

  "SignatureProviderAws" should {
    "return false on incorrect request" in {
      val awsSecretKey = AwsSecretKey("Qhd7Fe94KF0IwdnDr4zJEbLjqhfLKJat")
      val headers = List(
        RawHeader("authorization", """authorization: AWS4-HMAC-SHA256 Credential=4N4hgHnBjBCn4TLOd22UtNZUyB7bZ9LE/20181009/us-east-1/s3/aws4_request, SignedHeaders=content-md5;host;x-amz-content-sha256;x-amz-date;x-amz-security-token, Signature=f3088c6d3b97ef813db84a4fadc34311e377162426a3821f86cef7fee473add0"""),
        RawHeader("x-amz-security-token", "OfgzeOi5NOluFSWXv0acLTwvFkGamdzJ"),
        RawHeader("X-Amz-Date", "20181009T064543Z")
      )
      assert(!isUserAuthenticated(
        fakeIncomingHttpRequest(
          HttpMethods.GET,
          "/demobucket",
          headers),
        awsSecretKey))
    }

    "return true on correct V4 request" in {
      val awsSecretKey = AwsSecretKey("Qhd7Fe94KF0IwdnDr4zJEbLjqhfLKJry")
      val headers = List(
        RawHeader("authorization", """authorization: AWS4-HMAC-SHA256 Credential=4N4hgHnBjBCn4TLOd22UtNZUyB7bZ9LE/20181009/us-east-1/s3/aws4_request, SignedHeaders=content-md5;host;x-amz-content-sha256;x-amz-date;x-amz-security-token, Signature=f3088c6d3b97ef813db84a4fadc34311e377162426a3821f86cef7fee473add0"""),
        RawHeader("x-amz-security-token", "OfgzeOi5NOluFSWXv0acLTwvFkGamdzJ"),
        RawHeader("X-Amz-Date", "20181009T064543Z"),
        RawHeader("X-Amz-Content-SHA256", "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08"),
        RawHeader("Content-MD5", "CY9rzUYh03PK3k6DJie09g=="),
        RawHeader("Host", "127.0.0.1:8987")
      )

      val putRequest = fakeIncomingHttpRequest(HttpMethods.PUT, "/demobucket/fakeObject", headers)

      assert(isUserAuthenticated(putRequest, awsSecretKey))
    }

    "return true on correct V2 request" in {
      val awsSecretKey = AwsSecretKey("Qhd7Fe94KF0IwdnDr4zJEbLjqhfLKJry")
      val headers = List(
        RawHeader("authorization", """AWS 4N4hgHnBjBCn4TLOd22UtNZUyB7bZ9LE:FdqS+d5LG0g/Pkkw9jRtgl/Ovy0="""),
        RawHeader("x-amz-security-token", "OfgzeOi5NOluFSWXv0acLTwvFkGamdzJ"),
        RawHeader("Date", "Tue, 09 Oct 2018 07:15:24 GMT"),
        RawHeader("Content-Type", "application/octet-stream")
      )
      val getACLRequest = fakeIncomingHttpRequest(HttpMethods.GET, "/demobucket/", headers)

      assert(isUserAuthenticated(getACLRequest, awsSecretKey))
    }
  }

  import scala.collection.JavaConverters._

  "SignatureHelpersAws" should {

    "extractRequestParameters from RawQueryString with single subresource (v4)" in {

      val request = fakeIncomingHttpRequest(HttpMethods.GET, "/demobucket", Nil, "acl")
      val expectedResult = Map("acl" -> List[String]("").asJava).asJava

      assert(extractRequestParameters(request, "v4") == expectedResult)
    }

    "extractRequestParameters from RawQueryString with single subresource (v2)" in {
      val request = fakeIncomingHttpRequest(HttpMethods.GET, "/demobucket", Nil, "acl")
      val expectedResult = Map("acl" -> List.empty[String].asJava).asJava

      assert(extractRequestParameters(request, "v2") == expectedResult)
    }

    "extractRequestParameters from RawQueryString with single key value pair" in {
      val request = fakeIncomingHttpRequest(HttpMethods.GET, "/demobucket", Nil, "type=cool")
      val expectedResult = Map("type" -> List[String]("cool").asJava).asJava

      assert(extractRequestParameters(request, "v4") == expectedResult)
    }

    "extractRequestParameters from RawQueryString with multiple key value pairs" in {
      val request = fakeIncomingHttpRequest(HttpMethods.GET, "/demobucket", Nil, "type=cool&test=ok")
      val expectedResult = Map("type" -> List[String]("cool").asJava, "test" -> List[String]("ok").asJava).asJava

      assert(extractRequestParameters(request, "v4") == expectedResult)
    }

    "extractRequestParameters from RawQueryString with multiple key value pairs and Int" in {
      val request = fakeIncomingHttpRequest(HttpMethods.GET, "/demobucket", Nil, "list-type=2&prefix=&encoding-type=url")
      val expectedResult = Map(
        "list-type" -> List[String]("2").asJava,
        "prefix" -> List[String]("").asJava,
        "encoding-type" -> List[String]("url").asJava).asJava

      assert(extractRequestParameters(request, "v4") == expectedResult)
    }

    "getSignatureFromAuthorization v2 from authorization header" in {
      val authorization = """AWS 4N4hgHnBjBCn4TLOd22UtNZUyB7bZ9LE:FdqS+d5LG0g/Pkkw9jRtgl/Ovy0="""
      assert(getSignatureFromAuthorization(authorization) == "FdqS+d5LG0g/Pkkw9jRtgl/Ovy0=")
    }

    "getSignatureFromAuthorization v4 from authorization header" in {
      val authorization = """authorization: AWS4-HMAC-SHA256 Credential=4N4hgHnBjBCn4TLOd22UtNZUyB7bZ9LE/20181009/us-east-1/s3/aws4_request, SignedHeaders=content-md5;host;x-amz-content-sha256;x-amz-date;x-amz-security-token, Signature=f3088c6d3b97ef813db84a4fadc34311e377162426a3821f86cef7fee473add0"""
      assert(getSignatureFromAuthorization(authorization) == "f3088c6d3b97ef813db84a4fadc34311e377162426a3821f86cef7fee473add0")
    }

    "getCredentialFromAuthorization v2 from authorization header" in {
      val authorization = """AWS 4N4hgHnBjBCn4TLOd22UtNZUyB7bZ9LE:FdqS+d5LG0g/Pkkw9jRtgl/Ovy0="""
      assert(getCredentialFromAuthorization(authorization) == "4N4hgHnBjBCn4TLOd22UtNZUyB7bZ9LE")
    }

    "getCredentialFromAuthorization v4 from authorization header" in {
      val authorization = """authorization: AWS4-HMAC-SHA256 Credential=4N4hgHnBjBCn4TLOd22UtNZUyB7bZ9LE/20181009/us-east-1/s3/aws4_request, SignedHeaders=content-md5;host;x-amz-content-sha256;x-amz-date;x-amz-security-token, Signature=f3088c6d3b97ef813db84a4fadc34311e377162426a3821f86cef7fee473add0"""
      assert(getCredentialFromAuthorization(authorization) == "4N4hgHnBjBCn4TLOd22UtNZUyB7bZ9LE")
    }

    "getSignedHeaders from request" in {
      val authorization = """authorization: AWS4-HMAC-SHA256 Credential=4N4hgHnBjBCn4TLOd22UtNZUyB7bZ9LE/20181009/us-east-1/s3/aws4_request, SignedHeaders=content-md5;host;x-amz-content-sha256;x-amz-date;x-amz-security-token, Signature=f3088c6d3b97ef813db84a4fadc34311e377162426a3821f86cef7fee473add0"""
      assert(getSignedHeaders(authorization) == "content-md5;host;x-amz-content-sha256;x-amz-date;x-amz-security-token")
    }

    "getAWSHeaders from request for v2" in {
      val headers = List(
        RawHeader("authorization", """AWS 4N4hgHnBjBCn4TLOd22UtNZUyB7bZ9LE:FdqS+d5LG0g/Pkkw9jRtgl/Ovy0="""),
        RawHeader("x-amz-security-token", "OfgzeOi5NOluFSWXv0acLTwvFkGamdzJ"),
        RawHeader("Date", "Tue, 09 Oct 2018 07:15:24 GMT"),
        RawHeader("Content-Type", "application/octet-stream")
      )
      val request = fakeIncomingHttpRequest(HttpMethods.GET, "/demobucket/", headers)

      assert(getAWSHeaders(request).requestDate.getOrElse("") == "Tue, 09 Oct 2018 07:15:24 GMT")
      assert(getAWSHeaders(request).contentMD5.getOrElse("") == "")
      assert(getAWSHeaders(request).securityToken.getOrElse("") == "OfgzeOi5NOluFSWXv0acLTwvFkGamdzJ")
    }

    "getAWSHeaders from request for v4" in {
      val headers = List(
        RawHeader("authorization", """authorization: AWS4-HMAC-SHA256 Credential=4N4hgHnBjBCn4TLOd22UtNZUyB7bZ9LE/20181009/us-east-1/s3/aws4_request, SignedHeaders=content-md5;host;x-amz-content-sha256;x-amz-date;x-amz-security-token, Signature=f3088c6d3b97ef813db84a4fadc34311e377162426a3821f86cef7fee473add0"""),
        RawHeader("x-amz-security-token", "OfgzeOi5NOluFSWXv0acLTwvFkGamdzJ"),
        RawHeader("X-Amz-Date", "20181009T064543Z"),
        RawHeader("X-Amz-Content-SHA256", "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08"),
        RawHeader("Content-MD5", "CY9rzUYh03PK3k6DJie09g=="),
        RawHeader("Host", "127.0.0.1:8987")
      )

      val request = fakeIncomingHttpRequest(HttpMethods.PUT, "/demobucket/fakeObject", headers)
      val expectedResult = Map(
        "X-Amz-Content-SHA256" -> "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
        "Content-Md5" -> "CY9rzUYh03PK3k6DJie09g==",
        "X-Amz-Security-Token" -> "OfgzeOi5NOluFSWXv0acLTwvFkGamdzJ",
        "X-Amz-Date" -> "20181009T064543Z",
        "Host" -> "127.0.0.1:8987")

      assert(getAWSHeaders(request).signedHeadersMap == expectedResult)
    }
  }
}
