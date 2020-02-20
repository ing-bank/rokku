package com.ing.wbaa.rokku.proxy.persistence

import java.net.InetAddress

import akka.actor.{ ActorSystem, PoisonPill, Props }
import akka.http.scaladsl.model.HttpHeader.ParsingResult
import akka.http.scaladsl.model._
import akka.testkit.{ ImplicitSender, TestKit }
import com.ing.wbaa.rokku.proxy.data._
import com.ing.wbaa.rokku.proxy.persistence.HttpRequestRecorder.{ ExecutedRequestCmd, LatestRequests, LatestRequestsResult }
import org.scalatest.BeforeAndAfterAll
import org.scalatest.diagrams.Diagrams
import org.scalatest.wordspec.AnyWordSpecLike

import scala.collection.immutable

class HttpRequestRecorderSpec extends TestKit(ActorSystem("RequestRecorderTest")) with ImplicitSender
  with AnyWordSpecLike with Diagrams with BeforeAndAfterAll {

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }

  private def convertStringsToAkkaHeaders(headers: List[String]): immutable.Seq[HttpHeader] = headers.map { p =>
    val kv = p.split("=")
    HttpHeader.parse(kv(0), kv(1)) match {
      case ParsingResult.Ok(header, _) => header
      case ParsingResult.Error(error)  => throw new Exception(s"Unable to convert to HttpHeader: ${error.summary}")
    }
  }

  val requestRecorder = system.actorOf(Props(classOf[HttpRequestRecorder]), "localhost-1")

  val headers = List("Remote-Address=0:0:0:0:0:0:0:1:58170", "Host=localhost:8987",
    "X-Amz-Content-SHA256=02502914aca52472205417e4c418ee499ba39ca1b283d99da26e295df2eccf32",
    "User-Agent=aws-cli/1.16.30 Python/2.7.5 Linux/3.10.0-862.14.4.el7.x86_64 botocore/1.12.20",
    "Content-MD5=Wf7l+rCPsVw8eqc34kVJ1g==",
    "Authorization=AWS4-HMAC-SHA256 Credential=6r24619bHVWvrxR5AMHNkGZ6vNRXoGCP/20190704/us-east-1/s3/aws4_request",
    "SignedHeaders=content-md5;host;x-amz-content-sha256;x-amz-date;x-amz-security-token",
    "Signature=271dda503da6fcf04cc058cb514b28a6d522a9b712ab553bfb88fb7814ab082f")

  val httpRequest = HttpRequest(
    HttpMethods.PUT,
    Uri("http://127.0.0.1:8010/home/testuser/file34"),
    convertStringsToAkkaHeaders(headers),
    HttpEntity.Empty.withContentType(ContentTypes.`application/octet-stream`).toString(),
    HttpProtocols.`HTTP/1.1`
  )
  val userSTS = User(UserName("okUser"), Set(UserGroup("okGroup")), AwsAccessKey("accesskey"), AwsSecretKey("secretkey"), UserAssumeRole(""))
  val clientIPAddress = RemoteAddress(InetAddress.getByName("localhost"), Some(1234))

  "RequestRecorder" should {
    "persist Http request event" in {
      requestRecorder ! ExecutedRequestCmd(httpRequest, userSTS, clientIPAddress)
      requestRecorder ! LatestRequests(1)
      expectMsg(LatestRequestsResult(List(ExecutedRequestEvt(httpRequest, userSTS, clientIPAddress))))
      requestRecorder ! PoisonPill

      val requestRecorder1 = system.actorOf(Props(classOf[HttpRequestRecorder]), "localhost-2")
      requestRecorder1 ! LatestRequests(1)
      expectMsg(LatestRequestsResult(List(ExecutedRequestEvt(httpRequest, userSTS, clientIPAddress))))
    }
  }

}
