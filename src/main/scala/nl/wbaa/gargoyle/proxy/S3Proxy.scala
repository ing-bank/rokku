package nl.wbaa.gargoyle.proxy

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.directives.DebuggingDirectives
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.LazyLogging
import nl.wbaa.gargoyle.proxy.route._

import scala.concurrent.{ ExecutionContextExecutor, Future }

/**
 * Setup a proxy towards S3 (Ceph)
 *
 * @param s3Host Hostaddress where s3 runs
 * @param s3Port Port where s3 runs
 * @param system implicit actorSystem
 */
class S3Proxy(s3Host: String, s3Port: Int)(implicit system: ActorSystem = ActorSystem.create("gargoyle-s3proxy")) extends LazyLogging {

  implicit val ec: ExecutionContextExecutor = system.dispatcher

  def start(interface: String, port: Int): Future[Http.ServerBinding] = {
    implicit val mat: ActorMaterializer = ActorMaterializer()
    val http = Http(system)

    val allRoutes =
      // concat new routes here
      ProxyRoute(s3Host, s3Port).route

    // no debug
    //    bind = Await.result(http.bindAndHandle(allRoutes, proxyInterface, proxyPort), Duration.Inf)

    //debug all requests
    val bind = http.bindAndHandle(DebuggingDirectives.logRequest(("debug", Logging.InfoLevel))(allRoutes), interface, port)
    bind.foreach(b => logger.info(s"Server started on ${b.localAddress}"))
    bind
  }
}
