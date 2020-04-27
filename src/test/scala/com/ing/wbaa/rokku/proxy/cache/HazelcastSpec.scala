package com.ing.wbaa.rokku.proxy.cache

import akka.util.ByteString
import com.ing.wbaa.rokku.proxy.data.RequestId
import org.scalatest.diagrams.Diagrams
import org.scalatest.wordspec.AnyWordSpec

class HazelcastSpec extends AnyWordSpec with Diagrams with HazelcastCache {

  private implicit val id = RequestId("testRequestId")

  "Hazelcast Cache" should {
    "return empty BS for non existing object" in {
      assert(getObject("/bucket/nonexisting").isEmpty)
    }
    "add object to cache if BS non empty" in {
      val someObject = "/bucket/Object"
      putObject(someObject, ByteString("abc"))
      assert(getObject(someObject).isDefined)
    }
    "fail to get object from cache if BS empty" in {
      val someObject = "/bucket/emptyObject"
      putObject(someObject, ByteString.empty)
      assert(getObject(someObject).isEmpty)
    }
    "remove existing object from cache" in {
      val removedObject = "/bucket/ObjectRemoved"
      putObject(removedObject, ByteString("abc"))
      removeObject(removedObject)
      assert(getObject(removedObject).isEmpty)
    }
    "remove both head and existing object from cache (on AWS rm)" in {
      val removedObjectHead = "/bucket/ObjectRemoved1-head"
      val removedObject = "/bucket/ObjectRemoved1"
      putObject(removedObjectHead, ByteString("abc"))
      putObject(removedObject, ByteString("abc"))
      removeObject(removedObject)
      assert(getObject(removedObjectHead).isEmpty)
      assert(getObject(removedObject).isEmpty)
    }
  }

}
