package nl.wbaa.gargoyle.proxy.providers

/**
  * Interface for storage provider backend implementations.
  */
trait StorageProvider {

  def translateRequest()


}
