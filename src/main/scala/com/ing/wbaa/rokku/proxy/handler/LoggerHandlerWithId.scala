package com.ing.wbaa.rokku.proxy.handler

import akka.http.scaladsl.model.{ StatusCode, StatusCodes }
import com.ing.wbaa.rokku.proxy.data.RequestId
import com.ing.wbaa.rokku.proxy.metrics.MetricsFactory
import com.ing.wbaa.rokku.proxy.metrics.MetricsFactory._
import com.typesafe.scalalogging.Logger
import org.slf4j.{ LoggerFactory, MDC }

class LoggerHandlerWithId {

  @transient
  private lazy val log: Logger =
    Logger(LoggerFactory.getLogger(getClass.getName))

  private val requestIdKey = "request.id"
  private val statusCodeKey = "request.statusCode"

  def debug(message: String, args: Any*)(implicit id: RequestId): Unit = {
    MDC.put(requestIdKey, id.value)
    MDC.put(statusCodeKey, "-")
//    log.debug(message, args: _*)  - does not work :(
    args.length match {
      case 0 => log.debug(message)
      case 1 => log.debug(message, args(0))
      case 2 => log.debug(message, args(0), args(1))
      case 3 => log.debug(message, args(0), args(1), args(2))
      case 4 => log.debug(message, args(0), args(1), args(2), args(3))
      case _ => log.debug(message, args)
    }
    MDC.remove(requestIdKey)
    MDC.remove(statusCodeKey)
  }

  def a(x: Any*) = println(x.length)

  def info(message: String, args: Any*)(implicit id: RequestId): Unit = {
    MDC.put(requestIdKey, id.value)
    MDC.put(statusCodeKey, "-")
    //    log.info(message, args: _*)  - does not work :(
    args.length match {
      case 0 => log.info(message)
      case 1 => log.info(message, args(0))
      case 2 => log.info(message, args(0), args(1))
      case 3 => log.info(message, args(0), args(1), args(2))
      case 3 => log.info(message, args(0), args(1), args(2), args(3))
      case _ => log.info(message, args)
    }
    MDC.remove(requestIdKey)
    MDC.remove(statusCodeKey)
  }

  def warn(message: String, args: Any*)(implicit id: RequestId, statusCode: StatusCode = StatusCodes.Continue): Unit = {
    MDC.put(requestIdKey, id.value)
    MDC.put(statusCodeKey, statusCode.value)
    //    log.warn(message, args: _*)  - does not work :(
    args.length match {
      case 0 => log.warn(message)
      case 1 => log.warn(message, args(0))
      case 2 => log.warn(message, args(0), args(1))
      case 3 => log.warn(message, args(0), args(1), args(2))
      case 3 => log.warn(message, args(0), args(1), args(2), args(3))
      case _ => log.warn(message, args)
    }
    MDC.remove(requestIdKey)
    MDC.remove(statusCodeKey)
  }

  def error(message: String, args: Any*)(implicit id: RequestId, statusCode: StatusCode = StatusCodes.Continue): Unit = {
    MDC.put(requestIdKey, id.value)
    MDC.put(statusCodeKey, statusCode.value)
    countLogErrors(MetricsFactory.ERROR_REPORTED_TOTAL)
    //    log.error(message, args: _*)  - does not work :(
    args.length match {
      case 0 => log.error(message)
      case 1 => log.error(message, args(0))
      case 2 => log.error(message, args(0), args(1))
      case 3 => log.error(message, args(0), args(1), args(2))
      case 3 => log.error(message, args(0), args(1), args(2), args(3))
      case _ => log.error(message, args)
    }
    MDC.remove(requestIdKey)
    MDC.remove(statusCodeKey)
  }
}
