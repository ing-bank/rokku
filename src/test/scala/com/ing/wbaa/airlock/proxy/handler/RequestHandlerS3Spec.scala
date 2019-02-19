package com.ing.wbaa.airlock.proxy.handler

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.stream.scaladsl.{ Sink, Source }
import akka.stream.{ ActorMaterializer, Materializer }
import akka.util.ByteString
import com.ing.wbaa.airlock.proxy.config.{ AtlasSettings, StorageS3Settings }
import com.ing.wbaa.airlock.proxy.data._
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

  def isUserAuthorizedForRequest(request: S3Request, user: User): Boolean = {
    user match {
      case User(userName, _, _, _) if userName.value == "admin" => true
      case User(userName, _, _, _) if userName.value == "user1" =>
        request match {
          case S3Request(_, s3BucketPath, _, _, _, _) =>
            if (s3BucketPath.get.startsWith("/demobucket/user/user2")) false else true
        }
      case _ => true
    }
  }

  "Request Handler" should {
    "execute a request" that {
      "retries a request when forbidden and user needs to be created" in {
        val initialNumFiredRequests = numFiredRequests
        executeRequest(
          HttpRequest(),
          User(UserRawJson("u", Set.empty[String], "a", "s")),
          null
        ).map(_ => assert(numFiredRequests - initialNumFiredRequests == 2))
      }
    }
  }

  val listBucketXmlResponse: String = scala.io.Source.fromResource("listBucket.xml").mkString.stripMargin.trim

  val adminUser = User(UserRawJson("admin", Set.empty[String], "a", "s"))
  val user1 = User(UserRawJson("user1", Set.empty[String], "a", "s"))
  val s3Request = S3Request(AwsRequestCredential(AwsAccessKey(""), None), Uri.Path("/demobucket/user"), HttpMethods.GET)
  val data: Source[ByteString, NotUsed] = Source.single(ByteString.fromString(listBucketXmlResponse))

  "List bucket object response" should {
    "returns all objects to admin" in {
      data.via(filterRecursiveListObjects(adminUser, s3Request)).map(_.utf8String).runWith(Sink.seq).map(x => {
        assert(x.mkString.stripMargin.equals(listBucketXmlResponse))
      })
    }

    val filteredXml: String = scala.io.Source.fromResource("filteredListBucket.xml").mkString.stripMargin.trim
    "returns filtered object for user 1" in {
      data.via(filterRecursiveListObjects(user1, s3Request)).map(_.utf8String).runWith(Sink.seq).map(x => {
        assert(x.mkString.stripMargin.replaceAll("[\n\r\\s]", "")
          .equals(filteredXml.replaceAll("[\n\r\\s]", "")))
      })
    }
  }
}
