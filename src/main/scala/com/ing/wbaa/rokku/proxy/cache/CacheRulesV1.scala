package com.ing.wbaa.rokku.proxy.cache

import akka.http.scaladsl.model.{ HttpRequest, HttpResponse }
import com.ing.wbaa.rokku.proxy.config.StorageS3Settings
import com.ing.wbaa.rokku.proxy.data.RequestId
import com.ing.wbaa.rokku.proxy.handler.LoggerHandlerWithId
import com.ing.wbaa.rokku.proxy.handler.parsers.RequestParser.{ AWSRequestType, GetObjectRequestType, HeadObjectRequestType, ModifyObjectRequestType }
import com.ing.wbaa.rokku.proxy.util.S3Utils

/**
 * The first implementation of a cache roles
 * - in the cache can be only Get request for an object and only for specific users
 */
trait CacheRulesV1 {

  private val logger = new LoggerHandlerWithId

  def awsRequestFromRequest(request: HttpRequest): AWSRequestType
  def getMaxEligibleCacheObjectSizeInBytes(implicit id: RequestId): Long
  def getEligibleCachePaths(implicit id: RequestId): Array[String]

  protected[this] def storageS3Settings: StorageS3Settings

  /**
   * Check if object can be kept in cache
   *
   * @param request
   * @param id
   * @return true if the object can be in cache
   */
  def isEligibleToBeCached(request: HttpRequest)(implicit id: RequestId): Boolean = awsRequestFromRequest(request) match {
    case GetObjectRequestType() | HeadObjectRequestType() if isEligiblePath(request) =>
      logger.debug("isEligibleToBeCached = {}", request)
      true
    case _ =>
      logger.debug("isNotEligibleToBeCached = {}", request)
      false
  }

  /**
   * For PUT/POST/DELETE ... any modification methods the cache needs to be invalidated
   *
   * @param request
   * @param id
   */
  def isEligibleToBeInvalidated(request: HttpRequest)(implicit id: RequestId): Boolean = awsRequestFromRequest(request) match {
    case _: ModifyObjectRequestType =>
      logger.debug("cache need to be invalidated {}", request)
      true
    case _ =>
      logger.debug("nothing to be invalidated {}", request)
      false
  }

  /**
   * check if the response size is in the limit
   * @param response
   * @param id
   * @return true if the response content length is not bigger than the max
   */
  def isEligibleSize(response: HttpResponse)(implicit id: RequestId): Boolean = {
    val contentLength = response.entity.contentLengthOption.getOrElse(0L)
    logger.debug("content length {}", contentLength)
    contentLength < getMaxEligibleCacheObjectSizeInBytes
  }

  /**
   * check if the request path starts with the allowed ones from settings
   *
   * @param request
   * @return true if the request path is eligible
   */
  private def isEligiblePath(request: HttpRequest)(implicit id: RequestId) = {
    getEligibleCachePaths
      .exists(S3Utils.getPathNameFromUrlOrHost(request).startsWith(_))
  }

}
