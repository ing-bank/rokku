package nl.wbaa.gargoyle.proxy.route

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import com.typesafe.scalalogging.LazyLogging
import nl.wbaa.gargoyle.proxy.conf
import nl.wbaa.gargoyle.proxy.route.CustomDirectives._

case class GetRoute() extends LazyLogging with conf {

  // accepts all GET requests
  def route() =
    checkPermission { secretKey =>
      get {
        extractRequestContext { ctx =>
          redirect(ctx.request.uri.withAuthority(s3endpoint, s3endpoint_port), StatusCodes.TemporaryRedirect)
        }
      }
    }
}
