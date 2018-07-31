package com.ing.wbaa.gargoyle.proxy.providers

import com.ing.wbaa.gargoyle.proxy.data.{ Secret, User }

import scala.concurrent.Future

trait AuthenticationProvider {
  def getUser(accessKey: String): User = User("testuser")
  def isAuthenticated(accessKey: String, token: Option[String] = None): Future[Option[Secret]] = Future.successful(Some(Secret("secret")))
}
