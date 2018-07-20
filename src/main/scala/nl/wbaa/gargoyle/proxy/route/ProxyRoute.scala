package nl.wbaa.gargoyle.proxy.route

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse, StatusCodes }
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import com.typesafe.scalalogging.LazyLogging
import akka.http.scaladsl.server.Directives._
import nl.wbaa.gargoyle.proxy.handler.RequestHandler
import nl.wbaa.gargoyle.proxy.providers.{ AuthenticationProvider, AuthorizationProvider }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class ProxyRoute()(implicit system: ActorSystem, mat: Materializer) extends LazyLogging
  with AuthenticationProvider
  with AuthorizationProvider
  with RequestHandler {

  // no validation of request currently
  // once we get comfortable with get/put/del we can add permCheck
  val route: Route =
    withoutSizeLimit {
      extractClientIP { remoteAddress =>
        Route { ctx =>
          val requestProcessor: HttpRequest => Future[HttpResponse] = htr =>
            isAuthenticated("accesskey", Some("token")).flatMap {
              case None => Future(HttpResponse(StatusCodes.ProxyAuthenticationRequired))
              case Some(secret) =>
                if (validateUserRequest(htr, secret))
                  isAuthorized("accessMode", "path", "username")
                else Future(HttpResponse(StatusCodes.BadRequest))
            }.flatMap {
              case false => Future(HttpResponse(StatusCodes.Unauthorized))
              case true =>
                val newHtr = translateRequest(htr, remoteAddress)
                logger.debug(s"NEW: $newHtr")
                val response = Http().singleRequest(newHtr)
                response.map(r => logger.debug(s"RESPONSE: $r"))
                response
            }

          requestProcessor(ctx.request).flatMap(r => ctx.complete(r))
        }
      }
    }
}
