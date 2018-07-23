package nl.wbaa.gargoyle.proxy.handler

import java.net.InetAddress

import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{ HttpRequest, RemoteAddress }
import org.scalatest.{ DiagrammedAssertions, WordSpec }

class RequestHandlerTest extends WordSpec with DiagrammedAssertions {
  "Request Handler" should {
    "translate request" which {
      "add forward to uri and forward headers" in {
        val request = HttpRequest()
        val remoteAddress = RemoteAddress(InetAddress.getByName("192.168.3.12"))

        val result = new RequestHandler {
          override val s3Host: String = "1.2.3.4"
          override val s3Port: Int = 1234
        }.translateRequest(request, remoteAddress)
        val expected = request
          .withUri(request.uri.withAuthority("1.2.3.4", 1234))
          .withHeaders(RawHeader("X-Forwarded-For", "192.168.3.12"), RawHeader("X-Forwarded-Proto", "HTTP/1.1"))
        assert(result == expected)
      }
    }
  }
}
