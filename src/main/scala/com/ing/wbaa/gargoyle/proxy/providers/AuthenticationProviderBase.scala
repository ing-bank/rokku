package com.ing.wbaa.gargoyle.proxy.providers

import com.ing.wbaa.gargoyle.proxy.data.User

import scala.concurrent.Future

trait AuthenticationProviderBase {
  def getUser(accessKey: String): Future[Option[User]]
  def isAuthenticated(accessKey: String, token: String): Future[Boolean]
}
