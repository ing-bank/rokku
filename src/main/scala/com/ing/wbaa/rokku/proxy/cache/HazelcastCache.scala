package com.ing.wbaa.rokku.proxy.cache

import akka.http.scaladsl.model.HttpRequest
import akka.util.ByteString
import com.hazelcast.core.{ Hazelcast, HazelcastInstance }
import com.hazelcast.map.IMap
import com.hazelcast.query.Predicates
import com.ing.wbaa.rokku.proxy.data.RequestId
import com.ing.wbaa.rokku.proxy.handler.LoggerHandlerWithId
import com.typesafe.config.ConfigFactory
import com.ing.wbaa.rokku.proxy.handler.parsers.CacheHelpers.isHead

import scala.util.{ Failure, Success, Try }

object HazelcastCache {
  // implementation specific settings
  private val conf = ConfigFactory.load()
  private val cacheEnabled = conf.getBoolean("rokku.storage.s3.enabledCache")
  private val mapName = conf.getString("rokku.storage.s3.cacheDStructName")
}

trait HazelcastCache extends StorageCache {
  import HazelcastCache.{ mapName, cacheEnabled }

  private val logger = new LoggerHandlerWithId

  private val keyDelimiter = "-#r-end-path#-"

  private val hInstance: Option[HazelcastInstance] =
    if (cacheEnabled)
      Some(Hazelcast.newHazelcastInstance())
    else
      None

  private def getIMap: Option[IMap[String, ByteString]] = hInstance.map(h => h.getMap(mapName))

  override def getKey(request: HttpRequest)(implicit id: RequestId): String = {
    val headMethod = if (isHead(request)) "-head" else ""
    val rangeHeader = request.getHeader("Range").map[String](r => s"-r-${r.value()}").orElse("")
    request.uri.path.toString + keyDelimiter + rangeHeader + headMethod
  }

  override def getObject(key: String)(implicit id: RequestId): Option[ByteString] =
    Try {
      getIMap match {
        case Some(m) => m.getOrDefault(key, ByteString.empty)
        case None    => ByteString.empty
      }
    } match {
      case Success(bs) if bs.nonEmpty =>
        logger.debug(s"Object already in cache - key {}", key)
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
      getIMap.map(m => m.put(key, value))
    } match {
      case Success(_)  => logger.debug("Object added to cache, {}", key)
      case Failure(ex) => logger.debug("Failed to add object to cache, e: {}", ex.getMessage)
    }

  override def removeObject(key: String)(implicit id: RequestId): Unit =
    Try {
      getIMap.map(m => m.removeAll(Predicates.sql(s"__key like $key%")))
    } match {
      case Success(_)  => logger.debug("Object removed from cache, {}", key)
      case Failure(ex) => logger.debug("Failed to remove object from cache: {}", ex.getMessage)
    }
}
