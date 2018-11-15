package com.ing.wbaa.ranger.plugin.conditionevaluator

class AnyIpCidrMatcher extends AbstractIpCidrMatcher {
  protected val zero: Boolean = false
  protected def combine(a: Boolean, b: Boolean): Boolean = a || b
}
