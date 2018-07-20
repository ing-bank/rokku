package nl.wbaa.gargoyle.proxy.handler

import java.net.InetAddress

import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{HttpRequest, RemoteAddress}
import com.typesafe.config.ConfigFactory
import org.scalatest.{DiagrammedAssertions, WordSpec}

class RequestHandlerTest extends WordSpec with DiagrammedAssertions {
  private val configS3 = ConfigFactory.load().getConfig("s3.server")
  val s3Host: String = configS3.getString("host")
  val s3Port: Int = configS3.getInt("port")

  "Request Handler" should {
    "translate request" which {
      "add forward to uri and forward headers" in {
        val request = HttpRequest()
        val remoteAddress = RemoteAddress(InetAddress.getByName("192.168.3.12"))

        val result = new RequestHandler {}.translateRequest(request, remoteAddress)
        val expected = request
          .withUri(request.uri.withAuthority(s3Host, s3Port))
          .withHeaders(RawHeader("X-Forwarded-For", "192.168.3.12"))
        assert(result == expected)
      }
    }
  }
}
