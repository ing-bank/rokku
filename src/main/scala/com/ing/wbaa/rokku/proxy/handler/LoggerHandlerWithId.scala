package com.ing.wbaa.rokku.proxy.handler

import com.ing.wbaa.rokku.proxy.data.RequestId
import com.typesafe.scalalogging.Logger
import org.slf4j.{ LoggerFactory, MDC }

class LoggerHandlerWithId {

  @transient
  private lazy val log: Logger =
    Logger(LoggerFactory.getLogger(getClass.getName))

  private val requestIdKey = "request.id"

  def debug(message: String, args: Any*)(implicit id: RequestId): Unit = {
    MDC.put(requestIdKey, id.value)
    log.debug(message, args)
    MDC.remove(requestIdKey)
  }

  def info(message: String, args: Any*)(implicit id: RequestId): Unit = {
    MDC.put(requestIdKey, id.value)
    log.info(message, args)
    MDC.remove(requestIdKey)
  }

  def warn(message: String, args: Any*)(implicit id: RequestId): Unit = {
    MDC.put(requestIdKey, id.value)
    log.warn(message, args)
    MDC.remove(requestIdKey)
  }

  def error(message: String, args: Any*)(implicit id: RequestId): Unit = {
    MDC.put(requestIdKey, id.value)
    log.error(message, args)
    MDC.remove(requestIdKey)
  }

}
