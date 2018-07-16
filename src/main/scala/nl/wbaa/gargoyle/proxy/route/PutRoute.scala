//package nl.wbaa.gargoyle.proxy.route
//
//import akka.actor.ActorSystem
//import akka.http.scaladsl.model._
//import akka.http.scaladsl.server.Directives.{ complete, extractRequestContext, put }
//import com.typesafe.scalalogging.LazyLogging
//import nl.wbaa.gargoyle.proxy.route.CustomDirectives.checkPermission
//import nl.wbaa.gargoyle.proxy.handler.RequestHandler
//import nl.wbaa.gargoyle.proxy.providers.Secret
//
//case class PutRoute()(implicit system: ActorSystem) extends LazyLogging with RequestHandler {
//
//  /**
//   * put route is using alpakka streams to put object in S3 (multipartUpload)
//   *
//   * @return
//   */
//  def route() =
//    checkPermission { secretKey =>
//      put {
//        //response
//        extractRequestContext { ctx =>
//            if (validateUserRequest(ctx.request, Secret(secretKey))) {
//
//              complete("ok")
//            } else {
//              complete(StatusCodes.Unauthorized)
//            }
//          }
//        }
//      }
//}
