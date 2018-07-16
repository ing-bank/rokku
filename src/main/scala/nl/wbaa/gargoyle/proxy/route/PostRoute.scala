//package nl.wbaa.gargoyle.proxy.route
//
//import akka.actor.ActorSystem
//import akka.http.scaladsl.model._
//import akka.http.scaladsl.server.Directives.{ complete, post }
//import com.typesafe.scalalogging.LazyLogging
//import nl.wbaa.gargoyle.proxy.route.CustomDirectives.checkPermission
//
//case class PostRoute()(implicit system: ActorSystem) extends LazyLogging {
//
//  def route() =
//    checkPermission { tokenOk =>
//      post {
//        complete {
//          HttpResponse(
//            StatusCodes.OK,
//            entity = HttpEntity(ContentType(MediaTypes.`text/plain`, HttpCharsets.`UTF-8`), "ok")
//          )
//        }
//      }
//    }
//}
