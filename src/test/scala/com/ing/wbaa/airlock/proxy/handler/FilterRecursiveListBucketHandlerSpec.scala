package com.ing.wbaa.airlock.proxy.handler

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.model.{ HttpMethods, RemoteAddress, Uri }
import akka.stream.scaladsl.{ Sink, Source }
import akka.stream.{ ActorMaterializer, Materializer }
import akka.util.ByteString
import com.ing.wbaa.airlock.proxy.data._
import org.scalatest.{ AsyncWordSpec, DiagrammedAssertions }

import scala.concurrent.ExecutionContext

class FilterRecursiveListBucketHandlerSpec extends AsyncWordSpec with DiagrammedAssertions with FilterRecursiveListBucketHandler {

  implicit val system: ActorSystem = ActorSystem.create("test-system")
  override implicit val executionContext: ExecutionContext = system.dispatcher

  implicit def materializer: Materializer = ActorMaterializer()(system)

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

  val listBucketXmlResponse: String = scala.io.Source.fromResource("listBucket.xml").mkString.stripMargin.trim

  val adminUser = User(UserRawJson("admin", Set.empty[String], "a", "s"))
  val user1 = User(UserRawJson("user1", Set.empty[String], "a", "s"))
  val s3Request = S3Request(AwsRequestCredential(AwsAccessKey(""), None), Uri.Path("/demobucket/user"), HttpMethods.GET, RemoteAddress.Unknown, HeaderIPs())
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
