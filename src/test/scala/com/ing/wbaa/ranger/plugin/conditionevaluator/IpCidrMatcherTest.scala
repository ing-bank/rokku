package com.ing.wbaa.ranger.plugin.conditionevaluator

import org.apache.ranger.plugin.model.RangerPolicy.RangerPolicyItemCondition
import org.apache.ranger.plugin.policyengine.{ RangerAccessRequest, RangerAccessRequestImpl }
import org.scalatest.{ DiagrammedAssertions, WordSpec }

class IpCidrMatcherTest extends WordSpec with DiagrammedAssertions {

  import scala.collection.JavaConverters._

  def newIpCidrMatcher(cidrs: List[String]): IpCidrMatcher = {
    val testIpCidrClass = new IpCidrMatcher()
    testIpCidrClass.setPolicyItemCondition(new RangerPolicyItemCondition("cidr", cidrs.asJava))
    testIpCidrClass.init()
    testIpCidrClass
  }

  def newRangerRequest(remoteIp: String): RangerAccessRequest = {
    val rari = new RangerAccessRequestImpl()
    rari.setRemoteIPAddress(remoteIp)
    rari
  }

  "IpCidrMatcherTest" should {

    "match valid CIDR ranges" in {
      val newMatcher = newIpCidrMatcher(List("1.2.3.4/32"))
      val newRequest = newRangerRequest("1.2.3.4")
      assert(newMatcher.isMatched(newRequest))
    }

    "match all when conditions are null" in {
      val newMatcher = new IpCidrMatcher()
      newMatcher.setPolicyItemCondition(null)
      newMatcher.init()
      val newRequest = newRangerRequest("23.34.45.56")
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

    "don't match when Ip not in CIDR range" in {
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
