package com.ing.wbaa.airlock.proxy.handler

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
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
