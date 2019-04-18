package com.ing.wbaa.rokku.proxy.handler

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import FilterRecursiveMultiDelete._
import org.scalatest.{ AsyncWordSpec, DiagrammedAssertions }

import scala.concurrent.ExecutionContext

class FilterRecursiveMultiDeleteSpec extends AsyncWordSpec with DiagrammedAssertions {

  implicit val system: ActorSystem = ActorSystem.create("test-system")
  override implicit val executionContext: ExecutionContext = system.dispatcher

  implicit def materializer: ActorMaterializer = ActorMaterializer()(system)

  val multiDeleteRequestXml: String = scala.io.Source.fromResource("multiDeleteRequest.xml").mkString.stripMargin.trim
  val multiDeleteRequestV4Xml: String = scala.io.Source.fromResource("multiDeleteRequestV4.xml").mkString.stripMargin.trim
  val multiPartComplete: String = scala.io.Source.fromResource("multipartUploadComplete.xml").mkString.stripMargin.trim
  val data: Source[ByteString, NotUsed] = Source.single(ByteString.fromString(multiDeleteRequestXml))
  val dataV4: Source[ByteString, NotUsed] = Source.single(ByteString.fromString(multiDeleteRequestV4Xml))
  val otherData: Source[ByteString, NotUsed] = Source.single(ByteString.fromString(multiPartComplete))

  "multiDelete request" should {
    "should be parsed to objects list" in {
      exctractMultideleteObjectsFlow(data).map { r =>
        assert(r.contains("testuser/file1"))
        assert(r.contains("testuser/file2"))
        assert(r.contains("testuser/file3"))
      }
    }
    "v4 should be parsed to objects list" in {
      exctractMultideleteObjectsFlow(dataV4).map { r =>
        assert(r.contains("testuser/issue"))
        assert(!r.contains("true"))
      }
    }

    "should return empty list" in {
      exctractMultideleteObjectsFlow(otherData).map(r => assert(r == Vector()))
    }
  }
}
