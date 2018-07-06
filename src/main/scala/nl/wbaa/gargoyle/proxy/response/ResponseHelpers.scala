package nl.wbaa.gargoyle.proxy.response

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.StandardRoute

object ResponseHelpers {

  def stringComplete(response: String): StandardRoute = {
    complete {
      HttpResponse(
        StatusCodes.OK,
        entity = HttpEntity(ContentType(MediaTypes.`text/plain`, HttpCharsets.`UTF-8`), response)
      )
    }
  }

}
