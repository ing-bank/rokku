package com.ing.wbaa.airlock.proxy.util

import ch.qos.logback.classic.PatternLayout
import ch.qos.logback.classic.spi.ILoggingEvent

import scala.util.matching.Regex

class MaskingPatternLayout extends PatternLayout {
  import MaskingPatternLayout.Replace

  private var replace: Vector[Replace] = Vector()

  def addReplace(replace: Replace): Unit = {
    this.replace = this.replace :+ replace
  }

  override def doLayout(event: ILoggingEvent): String = {
    val message = super.doLayout(event)

    replace.foldLeft(message) { (message, replace) =>
      val pattern = replace.getPattern
      val replacement = replace.getReplacement

      pattern.replaceAllIn(message, replacement)
    }
  }
}

object MaskingPatternLayout {
  /*
   * Used for deserialization in logback.xml
   */
  class Replace {
    private var pattern: Regex = _
    private var replacement: String = _

    def getPattern: Regex = pattern
    def getReplacement: String = replacement

    override def toString = s"/${pattern}/${replacement}/"

    def setPattern(pattern: String): Unit = {
      this.pattern = new Regex(pattern)
    }
    def setReplacement(replacement: String): Unit = {
      this.replacement = replacement
    }
  }
}
