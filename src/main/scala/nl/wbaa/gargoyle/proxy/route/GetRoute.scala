package nl.wbaa.gargoyle.proxy.route

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import com.typesafe.scalalogging.LazyLogging
import nl.wbaa.gargoyle.proxy.handler.RequestHandler
import nl.wbaa.gargoyle.proxy.providers.Secret
import nl.wbaa.gargoyle.proxy.route.CustomDirectives._

import scala.util.{ Failure, Success, Try }

case class GetRoute()(implicit system: ActorSystem) extends LazyLogging with RequestHandler {

  // accepts all GET requests
  def route() =
    checkPermission { secretKey =>
      get {
        extractRequestContext { ctx =>
          if (validateUserRequest(ctx.request, Secret(secretKey))) {
            Try(translateRequest(ctx.request)) match {
              case Success(s3Response) =>
                complete(s3Response)
              case Failure(ex) => // all exceptions for now
                throw new Exception(ex.getMessage)
            }
          } else {
            complete(StatusCodes.Unauthorized)
          }
        }
      }
    }
}
