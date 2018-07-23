package nl.wbaa.gargoyle.proxy.providers

import akka.http.scaladsl.model.HttpRequest
import com.amazonaws.auth.AWSCredentials

/**
 * Interface for storage provider backend implementations.
 */
trait StorageProvider {

  def credentials: AWSCredentials
  def endpoint: String
  def translateRequest(request: HttpRequest): String
  def verifyS3Signature() // if we decide to use aws keys instead of token

}
