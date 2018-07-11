package nl.wbaa.gargoyle.proxy.providers

/**
 * Interface for security provider implementations.
 */
trait AuthorizationProvider {
  def isAuthorized(accessMode: String, path: String, username: String): Boolean = true
}
