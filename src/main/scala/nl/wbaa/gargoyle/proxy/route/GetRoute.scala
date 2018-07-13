package nl.wbaa.gargoyle.proxy.route

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import com.typesafe.scalalogging.LazyLogging
import nl.wbaa.gargoyle.proxy.handler.RequestHandler
import nl.wbaa.gargoyle.proxy.route.CustomDirectives._

case class GetRoute() extends LazyLogging with RequestHandler {

  // accepts all GET requests
  def route() =
    checkPermission { secretKey =>
      get {
        extractRequestContext { ctx =>
          redirect(ctx.request.uri.withAuthority("192.168.56.10", 8080), StatusCodes.TemporaryRedirect)
        }
      }
    }
}
