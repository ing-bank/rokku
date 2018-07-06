package nl.wbaa.gargoyle.proxy.providers

/**
  * Interface for users provider implementations.
  */
case class User(username: String) // we need to adjust this


trait UsersProvider {

  def sync(): List[User]
  def getUser(username: String): User
  def getUserSecret(username: String): String

}
