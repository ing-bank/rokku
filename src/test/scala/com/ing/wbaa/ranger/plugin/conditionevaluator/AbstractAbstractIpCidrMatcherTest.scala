package com.ing.wbaa.ranger.plugin.conditionevaluator

import org.apache.ranger.plugin.policyengine.{ RangerAccessRequest, RangerAccessRequestImpl }
import org.scalatest.diagrams.Diagrams
import org.scalatest.wordspec.AnyWordSpec

abstract class AbstractAbstractIpCidrMatcherTest extends AnyWordSpec with Diagrams {

  import scala.jdk.CollectionConverters._

  def newIpCidrMatcher(cidrs: List[String]): AbstractIpCidrMatcher

  def newRangerRequest(remoteIp: String, forwardedForIps: List[String] = Nil): RangerAccessRequest = {
    val rari = new RangerAccessRequestImpl()
    rari.setRemoteIPAddress(remoteIp)
    rari.setForwardedAddresses(forwardedForIps.asJava)
    rari
  }

  "IpCidrMatcherTest" should {

    "match valid CIDR ranges" in {
      val newMatcher = newIpCidrMatcher(List("1.2.3.4/32"))
      val newRequest = newRangerRequest("1.2.3.4")
      assert(newMatcher.isMatched(newRequest))
    }

    "match when X-Forwarded-For IPs are in range" in {
      val newMatcher = newIpCidrMatcher(List("1.1.0.0/16"))
      val newRequest = newRangerRequest("1.1.1.1", List("1.1.1.1", "1.1.1.2", "1.1.2.1"))
      assert(newMatcher.isMatched(newRequest))
    }

    "match all when conditions are empty" in {
      val newMatcher = newIpCidrMatcher(List())
      val newRequest = newRangerRequest("1.2.3.4")
      assert(newMatcher.isMatched(newRequest))
    }

    "match all when conditions contain a *" in {
      val newMatcher = newIpCidrMatcher(List("1.2.3.4/32", "*"))
      val newRequest = newRangerRequest("23.34.45.56")
      assert(newMatcher.isMatched(newRequest))
    }

    "not match when Ip not in CIDR range" in {
      val newMatcher = newIpCidrMatcher(List("1.2.3.4/32"))
      val newRequest = newRangerRequest("23.34.45.56")
      assert(!newMatcher.isMatched(newRequest))
    }

    "skip an invalid cidr range" in {
      val newMatcher = newIpCidrMatcher(List("1.2.3.4//32"))
      val newRequest = newRangerRequest("1.2.3.4")
      assert(!newMatcher.isMatched(newRequest))
    }

    "throw an exception when any IP is null" in {
      val newMatcher = newIpCidrMatcher(List("1.2.3.4/32"))
      val newRequest1 = newRangerRequest(null)
      val newRequest2 = newRangerRequest("1.2.3.4", List("1.1.1.1", null))
      assertThrows[Exception](newMatcher.isMatched(newRequest1))
      assertThrows[Exception](newMatcher.isMatched(newRequest2))
    }

    "not throw an exception when an IP is null but matches all" in {
      val newMatcher = newIpCidrMatcher(List())
      val newRequest1 = newRangerRequest(null)
      val newRequest2 = newRangerRequest("1.2.3.4", List("1.1.1.1", null))
      assert(newMatcher.isMatched(newRequest1))
      assert(newMatcher.isMatched(newRequest2))
    }
  }
}
