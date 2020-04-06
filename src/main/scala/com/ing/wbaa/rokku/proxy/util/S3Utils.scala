package com.ing.wbaa.rokku.proxy.util

import akka.http.scaladsl.model.{ HttpRequest, Uri }

object S3Utils {

  /**
   * To support "path" and "virtual" s3 access style we need to check if the bucket name is in the hostname or url
   * The method assumed that the hostname contains ".s3" and everything before ".s3" is the bucket name.
   *
   * @param httpRequest
   * @return path to an object
   */
  def getPathNameFromUrlOrHost(httpRequest: HttpRequest): String = {
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

  def getBucketName(fullPath: String): String = fullPath.split("/").filter(_.nonEmpty).head

  /**
   * Parses the request path and set the right aws s3 prefix and delimiter
   * e.g
   *   url = /bucketName/?prefix=foo/bar  -> s3path = /bucket/foo/bar
   * @param httpRequest
   * @return s3 path
   */
  def getS3FullPathWithBucketName(httpRequest: HttpRequest): Uri.Path = {
    val pathName: String = getPathNameFromUrlOrHost(httpRequest)
    httpRequest.uri.rawQueryString match {

      case Some(queryString) if queryString.contains("prefix") =>
        val queryPrefixPair = queryString
          .split("&")
          .filter(_.contains("prefix"))
          .head.split("=")

        val delimiter = queryString
          .split("&").find(_.contains("delimiter")) match {
            case Some(d) => d.split("=").last
            case None    => "/"
          }

        if (queryPrefixPair.length == 2) {
          Uri.Path(s"$pathName/${queryPrefixPair.last.replace(delimiter, "/")}")
        } else {
          Uri.Path(s"$pathName")
        }
      case _ => Uri.Path(s"$pathName")
    }
  }

  /**
   * Get s3 path without bucket name
   * @param s3Path - the path returned from {@link #getS3FullPathWithBucketName(httpRequest: HttpRequest)}
   * @return path on None if in there is only bucket name
   */
  def getS3PathWithoutBucketName(s3Path: String): Option[String] = {
    if (s3Path.length > 1) { Some(s3Path) } else { None }
  }

  /**
   * If the s3 path ends with slash there is no object request otherwise it is
   * @param s3Path - the path returned from {@link #getS3FullPathWithBucketName(httpRequest: HttpRequest)}
   * @return s3 object ful path or None
   */
  def getS3FullObjectPath(s3Path: String): Option[String] = {
    if (s3Path.endsWith("/") || s3Path.split("/").length < 3) {
      None
    } else {
      Some(s3Path.split("/").last)
    }
  }

}
