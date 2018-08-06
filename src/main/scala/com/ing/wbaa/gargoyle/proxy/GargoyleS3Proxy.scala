package com.ing.wbaa.gargoyle.proxy

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import com.ing.wbaa.gargoyle.proxy.api.{ HealthService, ProxyService }
import com.ing.wbaa.gargoyle.proxy.config.GargoyleHttpSettings
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

trait GargoyleS3Proxy extends LazyLogging with ProxyService {

  implicit def system: ActorSystem
  implicit lazy val materializer: ActorMaterializer = ActorMaterializer()(system)

  def httpSettings: GargoyleHttpSettings

  implicit val executionContext: ExecutionContext = system.dispatcher

  // The routes we serve.
  final val allRoutes = HealthService.route ~ proxyServiceRoute

  // Details about the server binding.
  lazy val startup: Future[Http.ServerBinding] =
    Http(system).bindAndHandle(allRoutes, httpSettings.httpBind, httpSettings.httpPort)
      .andThen {
        case Success(binding) => logger.info(s"Proxy service started listening: ${binding.localAddress}")
        case Failure(reason)  => logger.error("Proxy service failed to start.", reason)
      }

  def shutdown(): Future[Done] = {
    startup.flatMap(_.unbind)
      .andThen {
        case Success(_)      => logger.info("Proxy service stopped.")
        case Failure(reason) => logger.error("Proxy service failed to stop.", reason)
      }
  }
}
