package nl.wbaa.gargoyle.proxy.route

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.Directives._

object CustomDirectives {

  val validateToken: Directive1[String] =
    // exact header field to be determined
    optionalHeaderValueByName("authorization").flatMap {
      case Some(token) =>
        //todo: validate token logic here
        provide(token)
      case _ =>
        complete(StatusCodes.Unauthorized)
    }

}
