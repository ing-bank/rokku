package nl.wbaa.gargoyle.proxy.providers

/**
 * Interface for users provider implementations.
 */
case class User(username: String) // we need to adjust this
case class Secret(secretKey: String)

trait AuthenticationProvider {
  def getUser(accessKey: String): User = User("test")
  def isAuthenticated(accessKey: String, token: Option[String] = None): Option[Secret] = Some(Secret("secret"))

}
