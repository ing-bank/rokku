package com.ing.wbaa.rokku.proxy

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import com.ing.wbaa.rokku.proxy.api.{ HealthService, PostRequestActions, ProxyService }
import com.ing.wbaa.rokku.proxy.config.HttpSettings
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

trait RokkuS3Proxy extends LazyLogging with ProxyService with PostRequestActions with HealthService {

  protected[this] implicit def system: ActorSystem

  protected[this] def httpSettings: HttpSettings

  protected[this] implicit val executionContext: ExecutionContext = system.dispatcher

  // The routes we serve.
  final val allRoutes = healthRoute ~ proxyServiceRoute

  // Details about the server binding.
  lazy val startup: Future[Http.ServerBinding] = {
    Http(system).newServerAt(httpSettings.httpBind, httpSettings.httpPort).bindFlow(allRoutes)
      .andThen {
        case Success(binding) => logger.info(s"Proxy service started listening: ${binding.localAddress}")
        case Failure(reason)  => logger.error("Proxy service failed to start.", reason)
      }
  }

  def shutdown(): Future[Done] = {
    startup.flatMap(s => s.unbind())
      .andThen {
        case Success(_)      => logger.info("Proxy service stopped.")
        case Failure(reason) => logger.error("Proxy service failed to stop.", reason)
      }
  }
}
