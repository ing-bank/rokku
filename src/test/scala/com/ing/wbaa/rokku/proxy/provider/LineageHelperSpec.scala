package com.ing.wbaa.rokku.proxy.provider

import akka.stream.ActorMaterializer
import com.ing.wbaa.rokku.proxy.config.KafkaSettings
import com.ing.wbaa.rokku.proxy.data.RequestId
import com.ing.wbaa.rokku.proxy.provider.atlas.LineageHelpers
import org.scalatest.{ DiagrammedAssertions, PrivateMethodTester, WordSpec }

import scala.concurrent.ExecutionContext

class LineageHelperSpec extends WordSpec with DiagrammedAssertions with PrivateMethodTester {

  object LineageHelpersTest extends LineageHelpers {
    override protected[this] implicit val kafkaSettings: KafkaSettings = null
    override protected[this] implicit val materializer: ActorMaterializer = null
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

}
