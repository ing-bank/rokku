package nl.wbaa.gargoyle.proxy.handler

import java.net.InetAddress

import akka.actor.ActorSystem
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{ HttpRequest, RemoteAddress }
import nl.wbaa.gargoyle.proxy.config.GargoyleStorageS3Settings
import org.scalatest.{ DiagrammedAssertions, WordSpec }

class RequestHandlerTest extends WordSpec with DiagrammedAssertions {

  private[this] final implicit val system: ActorSystem = ActorSystem.create("test-system")

  "Request Handler" should {
    "translate request" which {
      "add forward to uri and forward headers" in {
        val request = HttpRequest()
        val remoteAddress = RemoteAddress(InetAddress.getByName("192.168.3.12"))

        val result = new RequestHandler {
          override val storageS3Settings: GargoyleStorageS3Settings = new GargoyleStorageS3Settings(system.settings.config) {
            override val storageS3Host: String = "1.2.3.4"
            override val storageS3Port: Int = 1234
          }
        }.translateRequest(request, remoteAddress)
        val expected = request
          .withUri(request.uri.withAuthority("1.2.3.4", 1234))
          .withHeaders(RawHeader("X-Forwarded-For", "192.168.3.12"), RawHeader("X-Forwarded-Proto", "HTTP/1.1"))
        assert(result == expected)
      }
    }
  }
}
