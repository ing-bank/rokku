package nl.wbaa.gargoyle.proxy

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.LazyLogging
import nl.wbaa.gargoyle.proxy.api.HealthService
import nl.wbaa.gargoyle.proxy.config.GargoyleSettings

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

class GargoyleS3Proxy private[GargoyleS3Proxy](settings: GargoyleSettings)(implicit system: ActorSystem) extends LazyLogging {
  private[this] implicit val executionContext: ExecutionContext = system.dispatcher

  // The routes we serve.
  final val allRoutes = HealthService.route

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
  def apply()(implicit system: ActorSystem): GargoyleS3Proxy = apply(settings = None)
  def apply(settings: GargoyleSettings)(implicit system: ActorSystem): GargoyleS3Proxy = apply(settings = Some(settings))
  private[this] def apply(settings: Option[GargoyleSettings])(implicit system: ActorSystem): GargoyleS3Proxy = {
    val gargoyleSettings = settings.getOrElse(GargoyleSettings(system))
    new GargoyleS3Proxy(gargoyleSettings)
  }
}
