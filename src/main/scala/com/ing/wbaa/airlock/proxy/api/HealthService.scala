package com.ing.wbaa.airlock.proxy.api

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import com.ing.wbaa.airlock.proxy.handler.radosgw.RadosGatewayHandler
import scala.concurrent.ExecutionContext
import scala.util.{ Failure, Success, Try }

trait HealthService extends RadosGatewayHandler {
  import akka.http.scaladsl.server.Directives._

  protected[this] implicit def system: ActorSystem
  protected[this] implicit def executionContext: ExecutionContext

  final val healthRoute: Route =
    path("ping") {
      get {
        Try(listAllBuckets) match {
          case Success(_)  => complete("pong")
          case Failure(ex) => complete(StatusCodes.InternalServerError -> s"storage not available - $ex")
        }
      }
    }
}
