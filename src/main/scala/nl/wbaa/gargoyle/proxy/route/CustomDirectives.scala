package nl.wbaa.gargoyle.proxy.route

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directive1, RequestContext, Route}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.BasicDirectives
import nl.wbaa.gargoyle.proxy.handler.RequestHandler
import nl.wbaa.gargoyle.proxy.providers.{AuthenticationProvider, AuthorizationProvider, Secret}
import nl.wbaa.gargoyle.proxy.route.CustomDirectives.checkPermission

object CustomDirectives extends AuthenticationProvider with AuthorizationProvider with RequestHandler {

    // exact header field to be determined
    val checkPermission: Directive1[String] =
    // exact header field to be determined
      optionalHeaderValueByName("authorization").flatMap {
        case Some(auth) =>
          //todo: validate token logic here
          val secretOpt = isAuthenticated("accessKey").getOrElse(Secret(""))

          if(isAuthorized("READ","/test","test")) {
            provide(secretOpt.secretKey)
          } else {
            complete(StatusCodes.Unauthorized)
          }
        case _ =>
          complete(StatusCodes.Unauthorized)
      }

}
