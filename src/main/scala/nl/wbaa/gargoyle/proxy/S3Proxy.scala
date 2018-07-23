package nl.wbaa.gargoyle.proxy

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.directives.DebuggingDirectives
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import nl.wbaa.gargoyle.proxy.route._

import scala.concurrent.duration.Duration
import scala.concurrent.{ Await, ExecutionContext }

class S3Proxy()(implicit system: ActorSystem = ActorSystem.create("gargoyle-s3proxy")) extends LazyLogging {
  import S3Proxy._

  implicit val ec: ExecutionContext = system.dispatcher
  private var bind: Http.ServerBinding = _

  def start(): Http.ServerBinding = {
    implicit val mat: ActorMaterializer = ActorMaterializer()
    val http = Http(system)

    val allRoutes =
      // concat new routes here
      ProxyRoute().route()
    //GetRoute.route()

    // interface 0.0.0.0 needed in case of docker

    // no debug
    //bind = Await.result(http.bindAndHandle(allRoutes, "0.0.0.0", port), Duration.Inf)

    //debug all requests
    bind = Await.result(http.bindAndHandle(DebuggingDirectives.logRequest(("debug", Logging.InfoLevel))(allRoutes), proxyInterface, proxyPort), Duration.Inf)
    logger.info("Server started")
    bind
  }
}

object S3Proxy {
  private val configProxy = ConfigFactory.load().getConfig("proxy.server")
  val proxyInterface: String = configProxy.getString("interface")
  val proxyPort: Int = configProxy.getInt("port")
}
