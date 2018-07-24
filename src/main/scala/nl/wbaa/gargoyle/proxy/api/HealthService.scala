package nl.wbaa.gargoyle.proxy
package api

trait HealthService {
  import akka.http.scaladsl.server.Directives._

  final val route =
    path("ping") {
      get {
        complete("pong")
      }
    }
}

object HealthService extends HealthService
