package nl.wbaa.gargoyle.proxy.providers

import scala.concurrent.Future

/**
 * Interface for users provider implementations.
 */
case class User(username: String) // we need to adjust this
case class Secret(secretKey: String)

trait AuthenticationProvider {
  def getUser(accessKey: String): Future[User] = Future.successful(User("test"))
  def isAuthenticated(accessKey: String, token: Option[String] = None): Future[Option[Secret]] = Future.successful(Some(Secret("secret")))
}
