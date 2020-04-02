package com.ing.wbaa.rokku.proxy.cache
import java.nio.charset.Charset

import akka.http.scaladsl.model.HttpRequest
import akka.util.ByteString
import com.ing.wbaa.rokku.proxy.data.RequestId
import com.ing.wbaa.rokku.proxy.handler.LoggerHandlerWithId

import scala.collection.mutable

/**
 * Example cache - do not use in production
 */
trait MemoryStorageCache extends StorageCache {

  private val logger = new LoggerHandlerWithId
  private val cache = mutable.Map.empty[String, ByteString]

  override def getKey(request: HttpRequest)(implicit id: RequestId): String = {
    //TODO key is very importent to be unique!
    s"${request.getUri().getPathString}-${request.getUri().queryString(Charset.forName("UTF-8"))}"
  }

  override def getObject(key: String)(implicit id: RequestId): Option[ByteString] = {
    logger.debug("get from cache {}", key)
    cache.get(key)
  }

  def getObject(request: HttpRequest)(implicit id: RequestId): Option[ByteString] = {
    val key = getKey(request)
    logger.debug("get from cache {}", key)
    cache.get(key)
  }

  override def putObject(key: String, value: ByteString)(implicit id: RequestId): Unit = {
    cache.put(key, value)
    logger.debug("stored in cache {}", key)
  }

  override def removeObject(key: String)(implicit id: RequestId): Unit = {
    cache.remove(key)
    logger.debug("removed from cache {}", key)
  }
}
