package com.ing.wbaa.rokku.proxy

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import com.ing.wbaa.rokku.proxy.api.{HealthService, PostRequestActions, ProxyServiceWithListAllBuckets}
import com.ing.wbaa.rokku.proxy.config.HttpSettings
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

trait RokkuS3Proxy extends LazyLogging with ProxyServiceWithListAllBuckets with PostRequestActions with HealthService {

  implicit protected[this] def system: ActorSystem
  implicit protected[this] lazy val materializer: ActorMaterializer = ActorMaterializer()(system)

  protected[this] def httpSettings: HttpSettings

  implicit protected[this] val executionContext: ExecutionContext = system.dispatcher

  // The routes we serve.
  final val allRoutes = healthRoute ~ proxyServiceRoute

  // Details about the server binding.
  lazy val startup: Future[Http.ServerBinding] =
    Http(system)
      .bindAndHandle(allRoutes, httpSettings.httpBind, httpSettings.httpPort)
      .andThen {
        case Success(binding) => logger.info(s"Proxy service started listening: ${binding.localAddress}")
        case Failure(reason)  => logger.error("Proxy service failed to start.", reason)
      }

  def shutdown(): Future[Done] =
    startup
      .flatMap(_.unbind)
      .andThen {
        case Success(_)      => logger.info("Proxy service stopped.")
        case Failure(reason) => logger.error("Proxy service failed to stop.", reason)
      }
}
