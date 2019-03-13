package com.ing.wbaa.airlock.proxy.util

import java.util

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.contrib.json.classic.JsonLayout

import scala.util.matching.Regex

class MaskingPatternJsonLayout extends JsonLayout {

  import MaskingPatternJsonLayout.Replace

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

  override def addCustomDataToJsonMap(map: util.Map[String, AnyRef], event: ILoggingEvent): Unit = {
    map.put("application_name", "airlock-proxy")
    super.addCustomDataToJsonMap(map, event)
  }
}

object MaskingPatternJsonLayout {

  /*
   * Used for deserialization in logback.xml
   */
  class Replace {
    private var pattern: Regex = _
    private var replacement: String = _

    def getPattern: Regex = pattern

    def getReplacement: String = replacement

    override def toString = s"/$pattern/$replacement/"

    def setPattern(pattern: String): Unit = {
      this.pattern = new Regex(pattern)
    }

    def setReplacement(replacement: String): Unit = {
      this.replacement = replacement
    }
  }

}
