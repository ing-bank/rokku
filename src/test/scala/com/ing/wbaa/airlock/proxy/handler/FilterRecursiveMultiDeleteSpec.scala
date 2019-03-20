package com.ing.wbaa.airlock.proxy.handler

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.ing.wbaa.airlock.proxy.handler.FilterRecursiveMultiDelete._
import org.scalatest.{ AsyncWordSpec, DiagrammedAssertions }

import scala.concurrent.ExecutionContext

class FilterRecursiveMultiDeleteSpec extends AsyncWordSpec with DiagrammedAssertions {

  implicit val system: ActorSystem = ActorSystem.create("test-system")
  override implicit val executionContext: ExecutionContext = system.dispatcher

  implicit def materializer: ActorMaterializer = ActorMaterializer()(system)

  val multiDeleteRequestXml: String = scala.io.Source.fromResource("multiDeleteRequest.xml").mkString.stripMargin.trim
  val multiPartComplete: String = scala.io.Source.fromResource("multipartUploadComplete.xml").mkString.stripMargin.trim
  val data: Source[ByteString, NotUsed] = Source.single(ByteString.fromString(multiDeleteRequestXml))
  val otherData: Source[ByteString, NotUsed] = Source.single(ByteString.fromString(multiPartComplete))

  "multiDelete request" should {
    "should be parsed to objects list" in {
      exctractMultideleteObjectsFlow(data).map { r =>
        assert(r.contains("testuser/file1"))
        assert(r.contains("testuser/file2"))
        assert(r.contains("testuser/file3"))
      }
    }

    "should return empty list" in {
      exctractMultideleteObjectsFlow(otherData).map(r => assert(r == Vector()))
    }
  }
}
