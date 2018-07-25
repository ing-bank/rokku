package nl.wbaa.gargoyle.proxy

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.http.scaladsl.server.Directives._
import com.typesafe.scalalogging.LazyLogging
import nl.wbaa.gargoyle.proxy.api.{ HealthService, ProxyService }
import nl.wbaa.gargoyle.proxy.config.{ GargoyleHttpSettings, GargoyleStorageS3Settings }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

class GargoyleS3Proxy private[GargoyleS3Proxy] (settings: GargoyleHttpSettings, storageS3Settings: GargoyleStorageS3Settings)(implicit system: ActorSystem) extends LazyLogging {
  private[this] implicit val executionContext: ExecutionContext = system.dispatcher

  // The routes we serve.
  final val allRoutes = HealthService.route ~ new ProxyService(storageS3Settings).route

  // Details about the server binding.
  final val bind: Future[Http.ServerBinding] = {
    implicit val materializer: ActorMaterializer = ActorMaterializer()(system)

    Http(system).bindAndHandle(allRoutes, settings.httpBind, settings.httpPort)
      .andThen {
        case Success(binding) => logger.info(s"Proxy service started listening: ${binding.localAddress}")
        case Failure(reason)  => logger.error("Proxy service failed to start.", reason)
      }
  }

  def shutdown(): Future[Done] = {
    bind.flatMap(_.unbind)
      .andThen {
        case Success(_)      => logger.info("Proxy service stopped.")
        case Failure(reason) => logger.error("Proxy service failed to stop.", reason)
      }
  }
}

object GargoyleS3Proxy {
  def apply()(implicit system: ActorSystem): GargoyleS3Proxy = apply(None, None)

  def apply(httpSettings: GargoyleHttpSettings, storageS3Settings: GargoyleStorageS3Settings)(implicit system: ActorSystem): GargoyleS3Proxy =
    apply(httpSettings = Some(httpSettings), storageS3Settings = Some(storageS3Settings))

  def apply(httpSettings: GargoyleHttpSettings)(implicit system: ActorSystem): GargoyleS3Proxy =
    apply(httpSettings = Some(httpSettings), storageS3Settings = None)

  def apply(storageS3Settings: GargoyleStorageS3Settings)(implicit system: ActorSystem): GargoyleS3Proxy =
    apply(httpSettings = None, storageS3Settings = Some(storageS3Settings))

  private[this] def apply(httpSettings: Option[GargoyleHttpSettings], storageS3Settings: Option[GargoyleStorageS3Settings])(implicit system: ActorSystem): GargoyleS3Proxy = {
    val gargoyleHttpSettings = httpSettings.getOrElse(GargoyleHttpSettings(system))
    val gargoyleStorageS3Settings = storageS3Settings.getOrElse(GargoyleStorageS3Settings(system))
    new GargoyleS3Proxy(gargoyleHttpSettings, gargoyleStorageS3Settings)
  }
}
