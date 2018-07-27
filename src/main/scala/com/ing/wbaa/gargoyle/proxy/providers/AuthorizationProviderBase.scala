package com.ing.wbaa.gargoyle.proxy.providers

import com.ing.wbaa.gargoyle.proxy.data.S3Request

/**
  * Interface for authorization provider implementations.
  */
trait AuthorizationProviderBase {
  def isAuthorized(request: S3Request): Boolean
}
