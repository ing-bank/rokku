package com.ing.wbaa.rokku.proxy.handler

import akka.http.scaladsl.model.{ StatusCode, StatusCodes }
import com.ing.wbaa.rokku.proxy.data.RequestId
import com.ing.wbaa.rokku.proxy.metrics.MetricsFactory
import com.ing.wbaa.rokku.proxy.metrics.MetricsFactory._
import com.typesafe.scalalogging.Logger
import org.slf4j.{ LoggerFactory, MDC }

import scala.collection.immutable.ArraySeq

class LoggerHandlerWithId {

  @transient
  private lazy val log: Logger =
    Logger(LoggerFactory.getLogger(getClass.getName))

  private val requestIdKey = "request.id"
  private val statusCodeKey = "request.statusCode"

  def debug(message: String, args: Any*)(implicit id: RequestId): Unit = {
    MDC.put(requestIdKey, id.value)
    MDC.put(statusCodeKey, "-")
    log.debug(message, args) //TODO logging args !
    MDC.remove(requestIdKey)
    MDC.remove(statusCodeKey)
  }

  def info(message: String, args: Any*)(implicit id: RequestId): Unit = {
    MDC.put(requestIdKey, id.value)
    MDC.put(statusCodeKey, "-")
    log.info(message, args) //TODO logging args !
    MDC.remove(requestIdKey)
    MDC.remove(statusCodeKey)
  }

  def warn(message: String, args: Any*)(implicit id: RequestId, statusCode: StatusCode = StatusCodes.Continue): Unit = {
    MDC.put(requestIdKey, id.value)
    MDC.put(statusCodeKey, statusCode.value)
    if (args.isInstanceOf[ArraySeq[_]])
      log.warn(message, args) //TODO logging args !
    else
      log.warn(message, args) //TODO logging args !
    MDC.remove(requestIdKey)
    MDC.remove(statusCodeKey)
  }

  def error(message: String, args: Any*)(implicit id: RequestId, statusCode: StatusCode = StatusCodes.Continue): Unit = {
    MDC.put(requestIdKey, id.value)
    MDC.put(statusCodeKey, statusCode.value)
    countLogErrors(MetricsFactory.ERROR_REPORTED_TOTAL)
    if (args.isInstanceOf[ArraySeq[_]])
      log.error(message, args) //TODO logging args !
    else
      log.error(message, args) //TODO logging args !
    MDC.remove(requestIdKey)
    MDC.remove(statusCodeKey)
  }
}
