package com.ing.wbaa.ranger.plugin.conditionevaluator

import org.apache.ranger.plugin.model.RangerPolicy.RangerPolicyItemCondition

class AnyIpCidrMatcherTest extends AbstractAbstractIpCidrMatcherTest {
  import scala.jdk.CollectionConverters._

  def newIpCidrMatcher(cidrs: List[String]): AbstractIpCidrMatcher = {
    val testIpCidrClass = new AnyIpCidrMatcher()
    testIpCidrClass.setPolicyItemCondition(new RangerPolicyItemCondition("cidrAnyUserIPs", cidrs.asJava))
    testIpCidrClass.init()
    testIpCidrClass
  }

  "AnyIpCidrMatcherTest" should {

    "match when any IP is in CIDR ranges" in {
      val newMatcher = newIpCidrMatcher(List("1.2.3.0/24"))
      val newRequest = newRangerRequest("1.2.2.4", List("1.2.2.0", "1.2.3.255"))
      assert(newMatcher.isMatched(newRequest))
    }

    "match all when conditions are null" in {
      val newMatcher = new AnyIpCidrMatcher()
      newMatcher.setPolicyItemCondition(null)
      newMatcher.init()
      val newRequest = newRangerRequest("23.34.45.56")
      assert(newMatcher.isMatched(newRequest))
    }

  }

}
