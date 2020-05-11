package com.ing.wbaa.rokku.proxy.cache

import akka.util.ByteString
import com.ing.wbaa.rokku.proxy.cache.HazelcastCacheWithConf._
import com.ing.wbaa.rokku.proxy.config.StorageS3Settings
import com.ing.wbaa.rokku.proxy.data.RequestId
import com.ing.wbaa.rokku.proxy.handler.LoggerHandlerWithId

import scala.concurrent.Future

object HazelcastCacheWithConf {
  val MAX_ELIGIBLE_CACHE_OBJECT_SIZE_IN_BYTES = "maxEligibleCacheObjectSizeInBytes"
  val ELIGIBLE_CACHE_PATHS = "eligibleCachePaths"
  val HEAD_CACHE_ENABLED = "headEnabled"
}

trait HazelcastCacheWithConf extends HazelcastCache {
  protected[this] def storageS3Settings: StorageS3Settings

  private val logger = new LoggerHandlerWithId

  private def getSettingAsString(key: String, map: String = "S3CacheConf")(implicit id: RequestId): Option[String] =
    getObject(key, map).map(_.utf8String)

  def getMaxEligibleCacheObjectSizeInBytes(implicit id: RequestId): Long = {
    val value = getSettingAsString(MAX_ELIGIBLE_CACHE_OBJECT_SIZE_IN_BYTES).map(_.toLong)
      .orElse(Some(storageS3Settings.maxEligibleCacheObjectSizeInBytes))
      .get
    logger.debug("Getting cache setting: {}", MAX_ELIGIBLE_CACHE_OBJECT_SIZE_IN_BYTES, value)
    value
  }

  //todo: consider invalidating entries on allowed path change
  def getEligibleCachePaths(implicit id: RequestId): Array[String] = {
    val value = getSettingAsString(ELIGIBLE_CACHE_PATHS).map(_.trim().split(","))
      .orElse(Some(storageS3Settings.eligibleCachePaths))
      .get
    logger.debug("Getting cache setting: {}", ELIGIBLE_CACHE_PATHS, value.toList)
    value
  }

  def getHeadEnabled(implicit id: RequestId): Boolean = {
    val value = getSettingAsString(HEAD_CACHE_ENABLED).map(_.toBoolean)
      .orElse(Some(true))
      .get
    logger.debug("Getting cache setting: {}", HEAD_CACHE_ENABLED, value)
    value
  }

  def setCacheParam(key: String, value: String, map: String)(implicit id: RequestId): Future[Unit] = {
    logger.debug("Setting cache setting: {}", key, value)
    Future.successful(putObject(key, ByteString(value), map))
  }

}
