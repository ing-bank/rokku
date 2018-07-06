package nl.wbaa.gargoyle.proxy.providers

import akka.http.scaladsl.model.HttpRequest

/**
  * Interface for storage provider backend implementations.
  */
trait StorageProvider {

  def translateRequest(request: HttpRequest)

}
