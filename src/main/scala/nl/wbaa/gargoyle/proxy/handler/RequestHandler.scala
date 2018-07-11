package nl.wbaa.gargoyle.proxy.handler

import akka.http.scaladsl.model.HttpRequest
import nl.wbaa.gargoyle.proxy.providers.Secret

class RequestHandler {

  def validateUserRequest(request: HttpRequest, secret: Secret): Boolean = ???
  def translateRequest(request: HttpRequest): HttpRequest = ???

}
