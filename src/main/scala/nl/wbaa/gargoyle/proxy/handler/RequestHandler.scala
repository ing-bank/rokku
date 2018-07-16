package nl.wbaa.gargoyle.proxy.handler

import akka.http.scaladsl.model.HttpRequest
import nl.wbaa.gargoyle.proxy.providers.Secret

trait RequestHandler {
  def validateUserRequest(request: HttpRequest, secret: Secret): Boolean = true
  def translateRequest(request: HttpRequest): HttpRequest = request
}
