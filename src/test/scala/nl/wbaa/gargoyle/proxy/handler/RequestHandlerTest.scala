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

        val result = new RequestHandler {}.translateRequest(request, remoteAddress)
        val expected = request
          .withUri(request.uri.withAuthority("1.2.3.4", 1000))
          .withHeaders(RawHeader("X-Forwarded-For", "192.168.3.12"))
        assert(result == expected)
      }
    }
  }
}
