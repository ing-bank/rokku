package nl.wbaa.gargoyle.proxy.providers

import scala.concurrent.Future

/**
 * Interface for security provider implementations.
 */
trait AuthorizationProvider {
  def isAuthorized(accessMode: String, path: String, username: String): Future[Boolean] = Future.successful(true)
}
