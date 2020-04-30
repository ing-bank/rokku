package com.ing.wbaa.rokku.proxy.cache

import com.ing.wbaa.rokku.proxy.handler.parsers.CacheHelpers._
import org.scalatest.diagrams.Diagrams
import org.scalatest.wordspec.AnyWordSpec

class CacheHelpersSpec extends AnyWordSpec with Diagrams {

  "Hazelcast Cache helpers" should {
    "returns non empty Entity if incorrect CL" in {
      generateFakeEntity(0).contentLengthOption.map(s => assert(s == 0))
    }
  }

}
