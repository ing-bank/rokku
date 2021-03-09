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

  protected val maxQueueSize: Long = ConfigFactory.load().getLong("rokku.storage.s3.request.queue.size")
  protected val maxQueueBeforeBlockInPercent: Int = ConfigFactory.load().getInt("rokku.storage.s3.request.queue.max.size.to.block.in.percent")
  private val currentQueueSize: AtomicLong = new AtomicLong(0)
  private val queuePerUser: TrieMap[String, AtomicLong] = TrieMap.empty[String, AtomicLong]

  def getQueue: AtomicLong = currentQueueSize

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
    var userRequestCount: Long = 0
    var queueRequestCount: Long = 0

    synchronized {
      userRequestCount = queuePerUser(user.userName.value).updateAndGet(x => if (x > 0) x - 1 else 0)
      queueRequestCount = currentQueueSize.updateAndGet(x => if (x > 0) x - 1 else 0)
      if (userRequestCount == 0) queuePerUser.remove(user.userName.value)
    }
    MetricsFactory.decrementRequestQueue(metricName(user))
    logDebug(user, queueRequestCount, userRequestCount, "decrement")
  }

  private def increment(user: User)(implicit id: RequestId): Unit = {
    var userRequestCount: Long = 0
    var queueRequestCount: Long = 0
    synchronized {
      userRequestCount = queuePerUser(user.userName.value).incrementAndGet()
      queueRequestCount = currentQueueSize.incrementAndGet()
    }
    MetricsFactory.incrementRequestQueue(metricName(user))
    logDebug(user, queueRequestCount, userRequestCount, "increment")
  }

  /**
   * @param user the user to check
   * @return true when the queue is not full
   *         and the user does not occupy the queue more than the maxQueueBeforeBlockInPercent param divided by number of users in current queue.
   */
  private def isAllowedToAddToRequestQueue(user: User) = {
    synchronized {
      queuePerUser.putIfAbsent(user.userName.value, new AtomicLong(0))
      val userRequests = queuePerUser(user.userName.value)
      val userOccupiedQueue = (100 * userRequests.get()) / maxQueueSize
      val maxQueueBeforeBlockInPercentPerUser = maxQueueBeforeBlockInPercent / queuePerUser.size
      val isOverflown = userOccupiedQueue >= maxQueueBeforeBlockInPercentPerUser
      currentQueueSize.get() < maxQueueSize && !isOverflown
    }
  }

  private def logDebug(user: User, queueRequestCount: Long, userRequestCount: Long, method: String)(implicit id: RequestId): Unit = {
    logger.debug("request queue = {}", queueRequestCount)
    logger.debug("user request queue = {}", method, user.userName.value, userRequestCount)
    logger.debug("active users size = {}", queuePerUser.size)
  }

  private def metricName(user: User): String = {
    MetricsFactory.REQUEST_QUEUE_OCCUPIED_BY_USER.replace(MetricsFactory.REQUEST_USER, user.userName.value)
  }
}
