package com.ing.wbaa.rokku.proxy.queue

import java.util.concurrent.atomic.AtomicLong

import com.ing.wbaa.rokku.proxy.data.{ RequestId, User }
import com.ing.wbaa.rokku.proxy.handler.LoggerHandlerWithId
import com.ing.wbaa.rokku.proxy.metrics.MetricsFactory
import com.typesafe.config.ConfigFactory

import scala.collection.concurrent.TrieMap

/**
 * Memory implementation of user request queue
 *
 */
trait MemoryUserRequestQueue extends UserRequestQueue {

  private val logger = new LoggerHandlerWithId

  protected val queueSize: Long = ConfigFactory.load().getLong("rokku.storage.s3.request.queue.size")
  protected val maxQueueBeforeBlockInPercent: Int = ConfigFactory.load().getInt("rokku.storage.s3.request.queue.max.size.to.block.in.percent")
  private val queue: AtomicLong = new AtomicLong(0)
  private val queuePerUser: TrieMap[String, AtomicLong] = TrieMap.empty[String, AtomicLong]

  def getQueue: AtomicLong = queue

  def getUserQueue: TrieMap[String, AtomicLong] = queuePerUser

  override def addIfAllowedUserToRequestQueue(user: User)(implicit id: RequestId): Boolean = {
    if (isAllowedToAddToRequestQueue(user)) {
      increment(user)
      true
    } else {
      false
    }
  }

  override def decrement(user: User)(implicit id: RequestId): Unit = {
    val userRequestCount = queuePerUser(user.userName.value).updateAndGet(x => if (x > 0) x - 1 else 0)
    val queueRequestCount = queue.updateAndGet(x => if (x > 0) x - 1 else 0)
    MetricsFactory.decrementRequestQueue(metricName(user))
    logDebug(user, queueRequestCount, userRequestCount, "decrement")
  }

  private def increment(user: User)(implicit id: RequestId): Unit = {
    val userRequestCount = queuePerUser(user.userName.value).incrementAndGet()
    val queueRequestCount = queue.incrementAndGet()
    MetricsFactory.incrementRequestQueue(metricName(user))
    logDebug(user, queueRequestCount, userRequestCount, "increment")
  }

  /**
   * @param user the user to check
   * @return true when the queue is not full and the user does not occupy the queue than the maxQueueBeforeBlockInPercent param
   */
  private def isAllowedToAddToRequestQueue(user: User) = {
    synchronized {
      queuePerUser.putIfAbsent(user.userName.value, new AtomicLong(0))
      val userRequests = queuePerUser(user.userName.value)
      val userOccupiedQueue = (100 * userRequests.get()) / queueSize
      val isOverflown = userOccupiedQueue >= maxQueueBeforeBlockInPercent
      queue.get() < queueSize && !isOverflown
    }
  }

  private def logDebug(user: User, queueRequestCount: Long, userRequestCount: Long, method: String)(implicit id: RequestId): Unit = {
    logger.debug("request queue = {}", queueRequestCount)
    logger.debug(s" user request queue = {}", method, user.userName.value, userRequestCount)
  }

  private def metricName(user: User): String = {
    MetricsFactory.REQUEST_QUEUE_OCCUPIED_BY_USER.replace(MetricsFactory.REQUEST_USER, s"${user.userName.value.head.toString}..")
  }
}
