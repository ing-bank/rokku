package nl.wbaa.gargoyle.proxy

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.LazyLogging
import nl.wbaa.gargoyle.proxy.providers.StorageProvider
import nl.wbaa.gargoyle.proxy.route._
import akka.http.scaladsl.server.Directives._

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class S3Proxy(port: Int, provider: StorageProvider)(implicit system: ActorSystem = ActorSystem.create("gargoyle-s3proxy")) extends LazyLogging {

  implicit val p = provider
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
    bind = Await.result(http.bindAndHandle(allRoutes, "0.0.0.0", port), Duration.Inf)
    bind
  }

}
