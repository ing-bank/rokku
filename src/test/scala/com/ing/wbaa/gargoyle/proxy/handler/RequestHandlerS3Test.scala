package com.ing.wbaa.gargoyle.proxy.handler

import java.net.InetAddress

import akka.actor.ActorSystem
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{ HttpRequest, RemoteAddress, Uri }
import com.ing.wbaa.gargoyle.proxy.config.GargoyleStorageS3Settings
import org.scalatest.{ DiagrammedAssertions, PrivateMethodTester, WordSpec }

import scala.concurrent.ExecutionContext

class RequestHandlerS3Test extends WordSpec with DiagrammedAssertions with PrivateMethodTester {

  private[this] final implicit val actorSystem: ActorSystem = ActorSystem.create("test-system")
  private[this] final implicit val ec: ExecutionContext = actorSystem.dispatcher

  "Request Handler" should {
    "translate request" which {
      "add forward to uri and forward headers" in {
        val request = HttpRequest()
        val remoteAddress = RemoteAddress(InetAddress.getByName("192.168.3.12"))

        val result = new RequestHandlerS3 {
          override implicit val system: ActorSystem = actorSystem
          override implicit val executionContext: ExecutionContext = ec
          override val storageS3Settings: GargoyleStorageS3Settings = new GargoyleStorageS3Settings(system.settings.config) {
            override val storageS3Authority: Uri.Authority = Uri.Authority(Uri.Host("1.2.3.4"), 1234)
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
