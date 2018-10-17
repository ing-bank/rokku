package com.ing.wbaa.airlock.proxy.handler

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import akka.stream.{ ActorMaterializer, Materializer }
import com.ing.wbaa.airlock.proxy.config.{ AtlasSettings, StorageS3Settings }
import com.ing.wbaa.airlock.proxy.data.{ User, UserRawJson }
import com.ing.wbaa.airlock.proxy.provider.LineageProviderAtlas
import org.scalatest.{ AsyncWordSpec, DiagrammedAssertions }

import scala.concurrent.{ ExecutionContext, Future }

class RequestHandlerS3Spec extends AsyncWordSpec with DiagrammedAssertions with RequestHandlerS3 with LineageProviderAtlas {

  override implicit val system: ActorSystem = ActorSystem.create("test-system")
  override implicit val executionContext: ExecutionContext = system.dispatcher
  override val storageS3Settings: StorageS3Settings = new StorageS3Settings(system.settings.config) {
    override val storageS3Authority: Uri.Authority = Uri.Authority(Uri.Host("1.2.3.4"), 1234)
  }
  override implicit def materializer: Materializer = ActorMaterializer()(system)
  override val atlasSettings: AtlasSettings = new AtlasSettings(system.settings.config)

  var numFiredRequests = 0
  override def fireRequestToS3(request: HttpRequest): Future[HttpResponse] = {
    numFiredRequests = numFiredRequests + 1
    Future.successful(HttpResponse(status = StatusCodes.Forbidden))
  }

  override def handleUserCreationRadosGw(userSTS: User): Boolean = true

  "Request Handler" should {
    "translate request" which {
      "add forward to uri and forward headers" in {
        val request = HttpRequest()
        val result = translateRequest(request, Some("192.168.3.12:1234"), Some("12.23.34.45"))

        val expected = request
          .withUri(request.uri.withAuthority("1.2.3.4", 1234))
          .withHeaders(RawHeader("X-Forwarded-For", "12.23.34.45, 192.168.3.12"), RawHeader("X-Forwarded-Proto", "HTTP/1.1"))

        assert(result == expected)
      }
    }

    "execute a request" that {
      "retries a request when forbidden and user needs to be created" in {
        val initialNumFiredRequests = numFiredRequests
        executeRequest(
          HttpRequest(),
          User(UserRawJson("u", None, "a", "s"))
        ).map(_ => assert(numFiredRequests - initialNumFiredRequests == 2))
      }
    }
  }
}
