package nl.wbaa.gargoyle.proxy.providers

case class S3Request() {
  def WRITE     = s"write"
  def READ      = s"read"
  def WRITE_ACP = s"write_acp"
  def READ_ACP  = s"read_acp"

  var path: String = ""
  var owner: String = ""
  var method : String = ""
  var accessType: String = ""
  var username: String = ""
  var userGroups: Array[String] = Array[String]()
  var clientIp: String = ""
  var remoteAddr: String = ""
  var fwdAddresses: Array[String] = Array[String]()
}

/**
  * Interface for security provider implementations.
  */
trait PolicyProvider {
  def isAccessible(request: S3Request) : Boolean
}
