package com.ing.wbaa.gargoyle.proxy.handler

import akka.http.scaladsl.model.{ HttpRequest, HttpResponse, RemoteAddress }
import com.ing.wbaa.gargoyle.proxy.data.Secret

import scala.concurrent.Future

trait RequestHandlerBase {
  def validateUserRequest(request: HttpRequest, secret: Secret): Boolean
  def executeRequest(request: HttpRequest, clientAddress: RemoteAddress): Future[HttpResponse]
}
