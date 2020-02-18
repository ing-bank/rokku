package com.ing.wbaa.rokku.proxy.provider

import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.RawHeader
import com.ing.wbaa.rokku.proxy.config.KafkaSettings
import com.ing.wbaa.rokku.proxy.data.{ BucketClassification, DirClassification, ObjectClassification, RequestId }
import com.ing.wbaa.rokku.proxy.provider.atlas.LineageHelpers
import org.scalatest.{ DiagrammedAssertions, PrivateMethodTester, WordSpec }

import scala.concurrent.ExecutionContext

class LineageHelperSpec extends WordSpec with DiagrammedAssertions with PrivateMethodTester {

  object LineageHelpersTest extends LineageHelpers {
    override protected[this] implicit val kafkaSettings: KafkaSettings = null
    override protected[this] implicit val executionContext: ExecutionContext = null
  }

  implicit val id = RequestId("1")

  "extractMetadataFromHeader" that {
    "return None for empty header" in {
      val result = LineageHelpersTest.extractMetadataHeader(None)
      assert(result.isEmpty)
    }

    "return None for wrong header" in {
      val result = LineageHelpersTest.extractMetadataHeader(Some("k,v"))
      assert(result.isEmpty)
      val result2 = LineageHelpersTest.extractMetadataHeader(Some("k=v,k2"))
      assert(result2.isEmpty)
      val result3 = LineageHelpersTest.extractMetadataHeader(Some("kv,=k2,v2"))
      assert(result3.isEmpty)
    }

    "return key and value for metadata header" in {
      val result = LineageHelpersTest.extractMetadataHeader(Some("k=v"))
      assert(result.contains(Map("k" -> "v")))
    }

    "return keys and values for metadata header" in {
      val result = LineageHelpersTest.extractMetadataHeader(Some("k1=v1,k2=v2"))
      assert(result.contains(Map("k1" -> "v1", "k2" -> "v2")))
    }
  }

  "extractClassifications" that {
    "returns bucket classifications" in {
      val request = HttpRequest().withUri("bucket").withHeaders(RawHeader(LineageHelpersTest.CLASSIFICATIONS_HEADER, "classification1"))
      val result = LineageHelpersTest.extractClassifications(request)
      assert(result.size == 1)
      assert(result contains BucketClassification())
      assert(result(BucketClassification()) == List("classification1"))
    }

    "returns dir classifications" in {
      val request = HttpRequest().withUri("bucket/dir1/").withHeaders(RawHeader(LineageHelpersTest.CLASSIFICATIONS_HEADER, "classification1,classification2"))
      val result = LineageHelpersTest.extractClassifications(request)
      assert(result.size == 1)
      assert(result contains DirClassification())
      assert(result(DirClassification()) == List("classification1", "classification2"))
    }

    "returns object classifications" in {
      val request = HttpRequest().withUri("bucket/obj").withHeaders(RawHeader(LineageHelpersTest.CLASSIFICATIONS_HEADER, "classification1,classification2,classification3"))
      val result = LineageHelpersTest.extractClassifications(request)
      assert(result.size == 1)
      assert(result contains ObjectClassification())
      assert(result(ObjectClassification()) == List("classification1", "classification2", "classification3"))
      val request2 = HttpRequest().withUri("bucket/dir1/obj").withHeaders(RawHeader(LineageHelpersTest.CLASSIFICATIONS_HEADER, "classification1"))
      val result2 = LineageHelpersTest.extractClassifications(request2)
      assert(result2.size == 1)
      assert(result2 contains ObjectClassification())
      assert(result2(ObjectClassification()) == List("classification1"))
    }
  }

}
