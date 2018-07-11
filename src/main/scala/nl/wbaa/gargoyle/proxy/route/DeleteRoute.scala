package nl.wbaa.gargoyle.proxy.route

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives.{ complete, delete }
import com.typesafe.scalalogging.LazyLogging
import nl.wbaa.gargoyle.proxy.providers.StorageProvider
import nl.wbaa.gargoyle.proxy.route.CustomDirectives.checkPermission

case class DeleteRoute()(implicit provider: StorageProvider) extends LazyLogging {

  def route() =
    checkPermission { tokenOk =>
      delete {
        complete {
          HttpResponse(
            StatusCodes.OK,
            entity = HttpEntity(ContentType(MediaTypes.`text/plain`, HttpCharsets.`UTF-8`), "ok")
          )
        }
      }
    }
}
