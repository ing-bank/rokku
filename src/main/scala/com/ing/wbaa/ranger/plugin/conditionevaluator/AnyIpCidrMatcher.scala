package com.ing.wbaa.ranger.plugin.conditionevaluator

class AnyIpCidrMatcher extends IpCidrMatcher {
  protected val zero: Boolean = false
  protected def combine(a: => Boolean, b: => Boolean): Boolean = a || b
}
