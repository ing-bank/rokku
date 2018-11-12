package com.ing.wbaa.ranger.plugin.conditionevaluator

import org.apache.ranger.plugin.model.RangerPolicy.RangerPolicyItemCondition

class AllIpCidrMatcherTest extends IpCidrMatcherTest {
  import scala.collection.JavaConverters._

  def newIpCidrMatcher(cidrs: List[String]): IpCidrMatcher = {
    val testIpCidrClass = new AnyIpCidrMatcher()
    testIpCidrClass.setPolicyItemCondition(new RangerPolicyItemCondition("cidr", cidrs.asJava))
    testIpCidrClass.init()
    testIpCidrClass
  }

  "AllIpCidrMatcherTest" should {

    "not match when any IP is not in CIDR ranges" in {
      val newMatcher = newIpCidrMatcher(List("1.2.3.0/24"))
      val newRequest = newRangerRequest("1.2.3.4", List("1.2.2.0", "1.2.3.255"))
      assert(newMatcher.isMatched(newRequest))
    }

    "match all when conditions are null" in {
      val newMatcher = new AllIpCidrMatcher()
      newMatcher.setPolicyItemCondition(null)
      newMatcher.init()
      val newRequest = newRangerRequest("23.34.45.56")
      assert(newMatcher.isMatched(newRequest))
    }



  }


}
