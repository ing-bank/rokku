package com.ing.wbaa.rokku.proxy.handler

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import com.ing.wbaa.rokku.proxy.config.{KafkaSettings, StorageS3Settings}
import com.ing.wbaa.rokku.proxy.data.{AwsAccessKey, AwsRequestCredential, HeaderIPs, RequestId, S3Request, User, UserRawJson}
import com.ing.wbaa.rokku.proxy.provider.LineageProviderAtlas
import com.ing.wbaa.rokku.proxy.queue.MemoryUserRequestQueue
import org.scalatest.{AsyncWordSpec, DiagrammedAssertions}

import scala.concurrent.{ExecutionContext, Future}

class RequestHandlerS3Spec extends AsyncWordSpec with DiagrammedAssertions with RequestHandlerS3 with LineageProviderAtlas with MemoryUserRequestQueue {

  override implicit val system: ActorSystem = ActorSystem.create("test-system")
  override implicit val executionContext: ExecutionContext = system.dispatcher
  override val storageS3Settings: StorageS3Settings = new StorageS3Settings(system.settings.config) {
    override val storageS3Authority: Uri.Authority = Uri.Authority(Uri.Host("1.2.3.4"), 1234)
  }

  override implicit val materializer: ActorMaterializer = ActorMaterializer()(system)
  override val kafkaSettings: KafkaSettings = new KafkaSettings(system.settings.config)

  implicit val requestId: RequestId = RequestId("test")

  var numFiredRequests = 0

  override def fireRequestToS3(request: HttpRequest)(implicit id: RequestId): Future[HttpResponse] = {
    numFiredRequests = numFiredRequests + 1
    Future.successful(HttpResponse(status = StatusCodes.Forbidden))
  }

  override def handleUserCreationRadosGw(userSTS: User)(implicit id: RequestId): Boolean = true

  def isUserAuthorizedForRequest(request: S3Request, user: User): Boolean = true

  override protected def filterResponse(request: HttpRequest, userSTS: User, s3request: S3Request, response: HttpResponse)(implicit id: RequestId): HttpResponse = null

  "Request Handler" should {
    "execute a request" that {
      "retries a request when forbidden and user needs to be created" in {
        val initialNumFiredRequests = numFiredRequests
        executeRequest(
          HttpRequest(),
          User(UserRawJson("u", Set.empty[String], "a", "s")),
          S3Request(AwsRequestCredential(AwsAccessKey(""), None), Uri.Path("/demobucket/user"), HttpMethods.GET, RemoteAddress.Unknown, HeaderIPs(), MediaTypes.`text/plain`)
        ).map(_ => assert(numFiredRequests - initialNumFiredRequests == 2))
      }
    }
  }

}
