package nl.wbaa.gargoyle.proxy.route

import akka.http.scaladsl.server.Directives._
import com.typesafe.scalalogging.LazyLogging
import nl.wbaa.gargoyle.proxy.providers.StorageProvider
import nl.wbaa.gargoyle.proxy.route.CustomDirectives._

case class GetRoute()(implicit provider: StorageProvider) extends LazyLogging {

  def route() =
    validateToken { tokenOk =>
      get {
        complete("ok")
      }
    }
}