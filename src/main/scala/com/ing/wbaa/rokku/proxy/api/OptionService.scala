package com.ing.wbaa.rokku.proxy.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.{ complete, options }
import akka.http.scaladsl.server.Route

trait OptionService {
  final val optionRoute: Route =
    options {
      complete(StatusCodes.OK)
    }

}
