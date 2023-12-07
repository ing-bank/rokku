package com.ing.wbaa.rokku.proxy.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.ing.wbaa.rokku.proxy.data.RequestId
import com.ing.wbaa.rokku.proxy.handler.LoggerHandlerWithId

trait TestService {

  private val logger = new LoggerHandlerWithId

  final val testRoute: Route =
    path("test200") {
      get {
        parameters("timeout".as[Long]) { (timeout) =>
          logger.debug("start test200")(RequestId("test200"))
          Thread.sleep(timeout)
          logger.debug("stop test200")(RequestId("test200"))
          complete(StatusCodes.OK -> "test")
        }
      }
    } ~ path("test502") {
      get {
        parameters("timeout".as[Long]) { (timeout) =>
          logger.debug("start test502")(RequestId("test200"))
          Thread.sleep(timeout)
          logger.debug("stop test502")(RequestId("test200"))
          complete(StatusCodes.BadGateway -> "502")
        }
      }
    } ~ path("test503") {
      get {
        parameters("timeout".as[Long]) { (timeout) =>
          logger.debug("start test503")(RequestId("test503"))
          Thread.sleep(timeout)
          logger.debug("stop test503")(RequestId("test503"))
          complete(StatusCodes.ServiceUnavailable -> "503")
        }
      }
    }

}
