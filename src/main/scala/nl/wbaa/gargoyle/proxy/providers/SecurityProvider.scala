package nl.wbaa.gargoyle.proxy.providers

/**
  * Interface for security provider implementations.
  */
trait SecurityProvider {

  def syncPolicies()
  def checkBucketPermission(bucket: String)

}
