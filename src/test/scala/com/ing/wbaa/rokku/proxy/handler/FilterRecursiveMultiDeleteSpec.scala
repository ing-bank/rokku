package com.ing.wbaa.rokku.proxy.handler

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.ing.wbaa.rokku.proxy.handler.FilterRecursiveMultiDelete._
import org.scalatest.diagrams.Diagrams
import org.scalatest.wordspec.AsyncWordSpec

import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext
import scala.util.Random

class FilterRecursiveMultiDeleteSpec extends AsyncWordSpec with Diagrams {

  implicit val system: ActorSystem = ActorSystem.create("test-system")
  override implicit val executionContext: ExecutionContext = system.dispatcher

  val multiDeleteRequestXml: String = scala.io.Source.fromResource("multiDeleteRequest.xml").mkString.stripMargin.trim
  val multiDeleteRequestV4Xml: String = scala.io.Source.fromResource("multiDeleteRequestV4.xml").mkString.stripMargin.trim
  val multiPartComplete: String = scala.io.Source.fromResource("multipartUploadComplete.xml").mkString.stripMargin.trim
  val data: Source[ByteString, NotUsed] = Source.single(ByteString.fromString(multiDeleteRequestXml))
  val dataV4: Source[ByteString, NotUsed] = Source.single(ByteString.fromString(multiDeleteRequestV4Xml))
  val otherData: Source[ByteString, NotUsed] = Source.single(ByteString.fromString(multiPartComplete))

  val numberOfObjects = 1000

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

    "should return correct size for large xml objects" in {
      val rand = new Random()
      val doc = new ListBuffer[String]()
      for (c <- 1 to numberOfObjects) doc +=
        s"<Object><Key>testuser/one/two/three/four/five/six/seven/eight/nine/ten/eleven/twelve/sub$c/${rand.alphanumeric.take(32).mkString}=${rand.alphanumeric.take(12).mkString}.txt</Key></Object>"

      exctractMultideleteObjectsFlow(Source.single(ByteString("<Delete>" + doc.mkString + "</Delete>"))).map { r =>
        assert(r.length == numberOfObjects)
      }
    }
  }
}
