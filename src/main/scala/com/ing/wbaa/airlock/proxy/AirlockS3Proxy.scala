package com.ing.wbaa.airlock.proxy

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import com.ing.wbaa.airlock.proxy.api.{ HealthService, ProxyServiceWithListAllBuckets }
import com.ing.wbaa.airlock.proxy.config.HttpSettings
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

trait AirlockS3Proxy extends LazyLogging with ProxyServiceWithListAllBuckets {

  protected[this] implicit def system: ActorSystem
  protected[this] implicit lazy val materializer: ActorMaterializer = ActorMaterializer()(system)

  protected[this] def httpSettings: HttpSettings

  protected[this] implicit val executionContext: ExecutionContext = system.dispatcher

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
