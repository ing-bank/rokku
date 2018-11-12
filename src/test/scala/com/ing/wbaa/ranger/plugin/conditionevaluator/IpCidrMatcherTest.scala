package com.ing.wbaa.ranger.plugin.conditionevaluator

import org.apache.ranger.plugin.policyengine.{ RangerAccessRequest, RangerAccessRequestImpl }
import org.scalatest.{ DiagrammedAssertions, WordSpec }

abstract class IpCidrMatcherTest extends WordSpec with DiagrammedAssertions {

  import scala.collection.JavaConverters._

  def newIpCidrMatcher(cidrs: List[String]): IpCidrMatcher

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
  }
}
