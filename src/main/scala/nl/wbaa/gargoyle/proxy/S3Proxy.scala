package nl.wbaa.gargoyle.proxy

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.LazyLogging
import nl.wbaa.gargoyle.proxy.providers.StorageProvider
import nl.wbaa.gargoyle.proxy.route._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.DebuggingDirectives

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class S3Proxy(interface: String, port: Int, provider: StorageProvider)
             (implicit system: ActorSystem = ActorSystem.create("gargoyle-s3proxy")) extends LazyLogging {

  implicit val p = provider
  implicit val ec = system.dispatcher
  private var bind: Http.ServerBinding = _

  def start(): Http.ServerBinding = {
    implicit val mat = ActorMaterializer()
    val http = Http(system)

    val allRoutes =
      // concat new routes here
      GetRoute().route() ~
        PostRoute().route() ~
        DeleteRoute().route() ~
        PutRoute().route()

    // interface 0.0.0.0 needed in case of docker

    // no debug
    //bind = Await.result(http.bindAndHandle(allRoutes, interface, port), Duration.Inf)

    //debug all requests
    bind = Await.result(
      http.bindAndHandle(DebuggingDirectives.logRequest(("debug", Logging.InfoLevel))(allRoutes), interface, port),
      Duration.Inf)

    bind
  }

}
