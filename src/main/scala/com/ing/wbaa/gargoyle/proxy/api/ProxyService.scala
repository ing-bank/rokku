package com.ing.wbaa.gargoyle.proxy.api

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.LazyLogging
import com.ing.wbaa.gargoyle.proxy.config.{GargoyleRangerSettings, GargoyleStorageS3Settings}
import com.ing.wbaa.gargoyle.proxy.data.{AccessType, S3Request}
import com.ing.wbaa.gargoyle.proxy.handler.RequestHandler
import com.ing.wbaa.gargoyle.proxy.providers.{AuthenticationProvider, AuthorizationProvider}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ProxyService(val storageS3Settings: GargoyleStorageS3Settings,
                   val rangerSettings: GargoyleRangerSettings)(implicit system: ActorSystem) extends LazyLogging
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
            isAuthenticated("accesskey", Some("token")).map {
              case None => HttpResponse(StatusCodes.ProxyAuthenticationRequired)
              case Some(secret) =>
                if (validateUserRequest(htr, secret))
                  isAuthorized(S3Request("demobucket", AccessType.read, "testuser", Set("testgroup")))
                else HttpResponse(StatusCodes.BadRequest)
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
