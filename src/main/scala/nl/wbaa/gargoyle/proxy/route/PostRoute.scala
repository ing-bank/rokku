package nl.wbaa.gargoyle.proxy.route

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives.{ complete, post }
import com.typesafe.scalalogging.LazyLogging
import nl.wbaa.gargoyle.proxy.providers.StorageProvider
import nl.wbaa.gargoyle.proxy.route.CustomDirectives.validateToken

case class PostRoute()(implicit provider: StorageProvider) extends LazyLogging {

  def route() =
    validateToken { tokenOk =>
      post {
        complete {
          HttpResponse(
            StatusCodes.OK,
            entity = HttpEntity(ContentType(MediaTypes.`text/plain`, HttpCharsets.`UTF-8`), "ok")
          )
        }
      }
    }
}
