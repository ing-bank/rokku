package nl.wbaa.gargoyle.proxy.handler

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import nl.wbaa.gargoyle.proxy.providers.Secret

trait RequestHandler {

  def validateUserRequest(request: HttpRequest, secret: Secret): Boolean = true

  def translateRequest(request: HttpRequest)(implicit system: ActorSystem) = {
    implicit val ex = system.dispatcher

    Http().singleRequest(request).map {
      case resp => resp //.entity.dataBytes
      //case _    => throw new Exception("Failed to execute request")
    }
  }

}
