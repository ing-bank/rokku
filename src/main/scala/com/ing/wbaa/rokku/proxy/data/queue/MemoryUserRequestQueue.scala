package com.ing.wbaa.rokku.proxy.data.queue
import java.util.concurrent.atomic.AtomicLong

import com.ing.wbaa.rokku.proxy.data.{RequestId, User}
import com.ing.wbaa.rokku.proxy.handler.LoggerHandlerWithId

import scala.collection.concurrent.TrieMap

trait MemoryUserRequestQueue extends UserRequestQueue {

  private val logger = new LoggerHandlerWithId

  private val queueSize: Long = 4
  private val maxQueueBeforeBlockInPercent: Byte = 50
  private val queue: AtomicLong = new AtomicLong(0)
  private val queuePerUser: TrieMap[String, AtomicLong] = TrieMap.empty[String, AtomicLong]

  def getQueue = queue
  def getUserQueue = queuePerUser


  override def doesUserOverflowQueue(user: User)(implicit id: RequestId): Boolean = {
    logger.debug("request queue = {}", queue.get())
    queuePerUser.putIfAbsent(user.userName.value, new AtomicLong(0))
    val userRequests = queuePerUser(user.userName.value)
    logger.debug("user request queue = {}",user.userName.value, userRequests)
    val userOccupiedQueue  = ((100 * userRequests.get()) / queueSize)
    logger.debug("user {} occupied {} queue", user, userOccupiedQueue)
    val isOverflown =  userOccupiedQueue >= maxQueueBeforeBlockInPercent
    if (queue.get() < queueSize && !isOverflown) {
      queuePerUser(user.userName.value).incrementAndGet()
      queue.incrementAndGet()
    logger.debug("increment request queue = {}", queue.get())
    logger.debug("increment user request queue = {}",user.userName.value, queuePerUser(user.userName.value))
    }
    isOverflown
  }

  override def decrement(user: User)(implicit id: RequestId): Unit = {
    logger.debug("decrement request queue = {}", queue.get())
    logger.debug("decrement user request queue = {}",user.userName.value, queuePerUser(user.userName.value))
    queuePerUser(user.userName.value).updateAndGet(x => if (x > 0) x-1 else 0)
    queue.updateAndGet(x => if (x > 0) x-1 else 0)
  }

}
