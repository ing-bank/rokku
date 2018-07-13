package nl.wbaa.gargoyle.proxy.route

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.Directives._
import nl.wbaa.gargoyle.proxy.providers.{ AuthenticationProvider, AuthorizationProvider, Secret }

object CustomDirectives extends AuthenticationProvider with AuthorizationProvider { RequestHandler =>

  // exact header field to be determined
  val checkPermission: Directive1[String] =
    // exact header field to be determined
    optionalHeaderValueByName("authorization").flatMap {
      case Some(auth) =>
        //todo: validate token logic here
        val secretOpt = isAuthenticated("accessKey").getOrElse(Secret(""))

        //println("auth: " + secretOpt.secretKey.split(" "))

        if (isAuthorized("READ", "/test", "test")) {
          provide(secretOpt.secretKey)
        } else {
          complete(StatusCodes.Unauthorized)
        }
      case _ =>
        complete(StatusCodes.Unauthorized)
    }

}
