package nl.wbaa.gargoyle.proxy.route

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import akka.stream.scaladsl.{ Sink, Source }
import com.typesafe.scalalogging.LazyLogging
import nl.wbaa.gargoyle.proxy.conf

import scala.concurrent.ExecutionContext.Implicits.global

case class ProxyRoute()(implicit system: ActorSystem, mat: Materializer) extends LazyLogging with conf {

  // no validation of request currently
  // once we get comfortable with get/put/del we can add permCheck
  def route() =
    Route { ctx =>

      val flow = Http(system).outgoingConnection(s3endpoint, s3endpoint_port)
      Source.single(ctx.request)
        .via(flow)
        .runWith(Sink.head)
        .flatMap(r => ctx.complete(r))
    }

}
