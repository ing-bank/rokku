package com.ing.wbaa.gargoyle.proxy.api

import akka.http.scaladsl.server.Route

trait HealthService {
  import akka.http.scaladsl.server.Directives._

  final val route: Route =
    path("ping") {
      get {
        complete("pong")
      }
    }
}

object HealthService extends HealthService
