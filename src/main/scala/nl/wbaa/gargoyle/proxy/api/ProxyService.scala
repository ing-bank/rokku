package nl.wbaa.gargoyle.proxy.api

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse, StatusCodes }
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.LazyLogging
import nl.wbaa.gargoyle.proxy.config.GargoyleStorageS3Settings
import nl.wbaa.gargoyle.proxy.handler.RequestHandler
import nl.wbaa.gargoyle.proxy.providers.{ AuthenticationProvider, AuthorizationProvider }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ProxyService(val storageS3Settings: GargoyleStorageS3Settings)(implicit system: ActorSystem) extends LazyLogging
  with AuthenticationProvider
  with AuthorizationProvider
  with RequestHandler {

  // no validation of request currently
  // once we get comfortable with get/put/del we can add permCheck
  import akka.http.scaladsl.server.Directives._

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
