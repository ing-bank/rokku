package com.ing.wbaa.rokku.proxy.util

import akka.http.scaladsl.model.HttpRequest

object S3Utils {

  /**
   * To support "path" and "virtual" s3 access style we need to check if the bucket name is in the hostname or url
   * The method assumed that the hostname contains ".s3" and everything before ".s3" is the bucket name.
   * @param httpRequest
   * @return path to an object
   */
  def getPathName(httpRequest: HttpRequest): String = {
    val host = httpRequest.uri.authority.host.toString()
    val path = if (httpRequest.uri.path.endsWithSlash) httpRequest.uri.path.toString().dropRight(1)
    else httpRequest.uri.path.toString()
    val virtualHostNameIndex = host.indexOf(".s3")
    if (virtualHostNameIndex > 0) {
      s"/${host.substring(0, virtualHostNameIndex)}$path"
    } else {
      path
    }
  }

}
