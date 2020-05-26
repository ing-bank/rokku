package com.ing.wbaa.rokku.proxy.handler

import akka.http.scaladsl.model.{ StatusCode, StatusCodes }
import com.ing.wbaa.rokku.proxy.data.RequestId
import com.ing.wbaa.rokku.proxy.metrics.MetricsFactory
import com.ing.wbaa.rokku.proxy.metrics.MetricsFactory._
import com.typesafe.scalalogging.Logger
import org.slf4j.{ LoggerFactory, MDC }

import scala.collection.mutable

class LoggerHandlerWithId {

  @transient
  private lazy val log: Logger =
    Logger(LoggerFactory.getLogger(getClass.getName))

  private val requestIdKey = "request.id"
  private val statusCodeKey = "request.statusCode"

  def debug(message: String, args: Any*)(implicit id: RequestId): Unit = {
    MDC.put(requestIdKey, id.value)
    MDC.put(statusCodeKey, "-")
    log.debug(message, args.asInstanceOf[mutable.WrappedArray[AnyRef]]: _*)
    MDC.remove(requestIdKey)
    MDC.remove(statusCodeKey)
  }

  def a(x: Any*) = println(x.length)

  def info(message: String, args: Any*)(implicit id: RequestId): Unit = {
    MDC.put(requestIdKey, id.value)
    MDC.put(statusCodeKey, "-")
    log.info(message, args.asInstanceOf[mutable.WrappedArray[AnyRef]]: _*)
    MDC.remove(requestIdKey)
    MDC.remove(statusCodeKey)
  }

  def warn(message: String, args: Any*)(implicit id: RequestId, statusCode: StatusCode = StatusCodes.Continue): Unit = {
    MDC.put(requestIdKey, id.value)
    MDC.put(statusCodeKey, statusCode.value)
    log.warn(message, args.asInstanceOf[mutable.WrappedArray[AnyRef]]: _*)
    MDC.remove(requestIdKey)
    MDC.remove(statusCodeKey)
  }

  def error(message: String, args: Any*)(implicit id: RequestId, statusCode: StatusCode = StatusCodes.Continue): Unit = {
    MDC.put(requestIdKey, id.value)
    MDC.put(statusCodeKey, statusCode.value)
    countLogErrors(MetricsFactory.ERROR_REPORTED_TOTAL)
    log.error(message, args.asInstanceOf[mutable.WrappedArray[AnyRef]]: _*)
    MDC.remove(requestIdKey)
    MDC.remove(statusCodeKey)
  }
}
