package nl.wbaa.gargoyle.proxy.route

import akka.http.scaladsl.model.{HttpCharsets, _}
import akka.http.scaladsl.server.Directives._
import com.typesafe.scalalogging.LazyLogging
import nl.wbaa.gargoyle.proxy.providers.StorageProvider
import nl.wbaa.gargoyle.proxy.route.CustomDirectives._

case class GetRoute()(implicit provider: StorageProvider) extends LazyLogging {

  // accepts all GET requests
  def route() =
    validateToken { tokenOk =>
      get {
        complete {
          HttpResponse(
            StatusCodes.OK,
            entity = HttpEntity(ContentType(MediaTypes.`text/plain`, HttpCharsets.`UTF-8`), "ok")
          )
        }
      }
    }
}