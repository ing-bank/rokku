package com.ing.wbaa.rokku.proxy.cache

import akka.http.scaladsl.model.HttpRequest
import akka.util.ByteString
import com.hazelcast.config.Config
import com.hazelcast.core.{ Hazelcast, HazelcastInstance }
import com.hazelcast.map.IMap
import com.ing.wbaa.rokku.proxy.config.StorageS3Settings
import com.ing.wbaa.rokku.proxy.data.RequestId
import com.ing.wbaa.rokku.proxy.handler.LoggerHandlerWithId

import scala.util.{ Failure, Success, Try }

trait HazelcastCache extends StorageCache {

  protected[this] def storageS3Settings: StorageS3Settings

  private val logger = new LoggerHandlerWithId

  private val clientConfig: Config = new Config(storageS3Settings.cacheInstanceName)

  private val hInstance: HazelcastInstance = Hazelcast.newHazelcastInstance(clientConfig)

  private val getIMap: IMap[String, ByteString] = hInstance.getMap("S3Cache")

  override def getKey(request: HttpRequest)(implicit id: RequestId): String = request.uri.path.toString

  override def getObject(key: String)(implicit id: RequestId): Option[ByteString] =
    Try {
      getIMap.getOrDefault(key, ByteString.empty)
    } match {
      case Success(bs) if !bs.isEmpty =>
        logger.debug("Object already in cache")
        Some(bs)
      case Success(_) =>
        logger.debug("Object not found in cache")
        None
      case Failure(ex) =>
        logger.debug("Failed to get object from cache, e: {}", ex.getMessage)
        None
    }

  override def putObject(key: String, value: ByteString)(implicit id: RequestId): Unit =
    Try {
      getIMap.put(key, value)
    } match {
      case Success(_)  => logger.debug("Object added to cache, {}", key)
      case Failure(ex) => logger.debug("Failed to add object to cache, e: {}", ex.getMessage)
    }

  override def removeObject(key: String)(implicit id: RequestId): Unit =
    Try {
      getIMap.delete(key)
    } match {
      case Success(_)  => logger.debug("Object removed from cache, {}", key)
      case Failure(ex) => logger.debug("Failed to remove object from cache: {}", ex.getMessage)
    }
}
