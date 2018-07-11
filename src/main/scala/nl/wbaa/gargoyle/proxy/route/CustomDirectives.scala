package nl.wbaa.gargoyle.proxy.route

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directive1, RequestContext, Route}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.BasicDirectives
import nl.wbaa.gargoyle.proxy.handler.RequestHandler
import nl.wbaa.gargoyle.proxy.providers.{AuthenticationProvider, AuthorizationProvider, Secret}
import nl.wbaa.gargoyle.proxy.route.CustomDirectives.validateRequest

object CustomDirectives extends AuthenticationProvider with AuthorizationProvider with RequestHandler {

    // exact header field to be determined
    val validateRequest: Directive1[String] =
    // exact header field to be determined
      optionalHeaderValueByName("authorization").flatMap {
        case Some(auth) =>
          //todo: validate token logic here
          val secretOpt = isAuthenticated("accessKey")

          if(isAuthorized("READ","/test","test")) {
            provide(auth)
          } else {
            complete(StatusCodes.Unauthorized)
          }
        case _ =>
          complete(StatusCodes.Unauthorized)
      }

    val byRequest = BasicDirectives.extractRequestContext.flatMap { ctx=>
      val request = ctx.request
      val accessKey = request.getHeader("authorization").get // contains Credential and Signature

      BasicDirectives.mapResponse{ resp =>
        resp
      }
    }



}
