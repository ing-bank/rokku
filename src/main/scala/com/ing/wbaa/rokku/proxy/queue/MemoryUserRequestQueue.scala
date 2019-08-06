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
      logDebug(user)
      true
    } else {
      false
    }
  }

  override def decrement(user: User)(implicit id: RequestId): Unit = {
    queuePerUser(user.userName.value).updateAndGet(x => if (x > 0) x - 1 else 0)
    queue.updateAndGet(x => if (x > 0) x - 1 else 0)
    MetricsFactory.decrementRequestQueue(metricName(user))
    logDebug(user, "decrement")
  }

  private def increment(user: User)(implicit id: RequestId): Unit = {
    queuePerUser(user.userName.value).incrementAndGet()
    queue.incrementAndGet()
    MetricsFactory.incrementRequestQueue(metricName(user))
    logDebug(user, "increment")
  }

  /**
   * @param user the user to check
   * @return true when the queue is not full and the user does not occupy the queue than the maxQueueBeforeBlockInPercent param
   */
  private def isAllowedToAddToRequestQueue(user: User) = {
    queuePerUser.putIfAbsent(user.userName.value, new AtomicLong(0))
    val userRequests = queuePerUser(user.userName.value)
    val userOccupiedQueue = (100 * userRequests.get()) / queueSize
    val isOverflown = userOccupiedQueue >= maxQueueBeforeBlockInPercent
    queue.get() < queueSize && !isOverflown
  }

  private def logDebug(user: User, method: String = "")(implicit id: RequestId): Unit = {
    logger.debug("request queue = {}", queue.get())
    logger.debug("user request queue = {}",method, user.userName.value, queuePerUser(user.userName.value).get())
  }

  private def metricName(user: User): String = {
    MetricsFactory.REQUEST_QUEUE_OCCUPIED_BY_USER.replace(MetricsFactory.REQUEST_USER, s"${user.userName.value.head.toString}..")
  }
}
