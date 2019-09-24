package com.ing.wbaa.rokku.proxy.queue

import com.ing.wbaa.rokku.proxy.data.{ AwsAccessKey, AwsSecretKey, RequestId, User, UserName }
//import com.ing.wbaa.rokku.proxy.queue.MemoryUserRequestQueue
import org.scalatest.{ DiagrammedAssertions, WordSpec }

class MemoryUserRequestQueueTest extends WordSpec with DiagrammedAssertions {

  implicit val id: RequestId = RequestId("test")
  private val queueRequest = new MemoryUserRequestQueue {
    override protected val maxQueueSize: Long = 4
    override protected val maxQueueBeforeBlockInPercent: Int = 50
  }
  private val userOne = User(UserName("userOne"), Set.empty, AwsAccessKey(""), AwsSecretKey(""))
  private val userTwo = User(UserName("userTwo"), Set.empty, AwsAccessKey(""), AwsSecretKey(""))
  private val userThree = User(UserName("userThree"), Set.empty, AwsAccessKey(""), AwsSecretKey(""))
  private val userFour = User(UserName("userFour"), Set.empty, AwsAccessKey(""), AwsSecretKey(""))
  private val userFive = User(UserName("userFive"), Set.empty, AwsAccessKey(""), AwsSecretKey(""))

  "MemoryUserRequestQueue" should {

    "increment/decrement queue" in {
      //add first userOne request queueSize = 1, userOne in queue = 1 maxQueueBeforeBlockInPercent = 50%
      increment(userOne, canBeAdded = true, queueSize = 1, userQueueSize = 1)
      //add second userOne request queueSize = 2, userOne in queue = 2 maxQueueBeforeBlockInPercent = 50%
      increment(userOne, canBeAdded = true, queueSize = 2, userQueueSize = 2)
      //add first userTwo request  queueSize = 3, userOne in queue = 2, userTwo in queue =1, maxQueueBeforeBlockInPercent = 50%/2 = 25%
      increment(userTwo, canBeAdded = true, queueSize = 3, userQueueSize = 1)
      //cannot add second userTwo request because userTwo occupied 25% of the queue
      increment(userTwo, canBeAdded = false, queueSize = 3, userQueueSize = 1)
      //cannot add third userOne request because userOne occupied 50% of the queue and is more than allowed now (with two active users)
      increment(userOne, canBeAdded = false, queueSize = 3, userQueueSize = 2)
      //add first userThree request  queueSize = 4, userOne in queue = 2, userTwo=1, userThree=1, maxQueueBeforeBlockInPercent = 50%/3 = 17%
      increment(userThree, canBeAdded = true, queueSize = 4, userQueueSize = 1)
      //cannot add second userThree request because the queue is full and userThree occupied 25% of the queue
      increment(userThree, canBeAdded = false, queueSize = 4, userQueueSize = 1)
      //userOne finished one request so queueSize = 3, userOne in queue = 1, userTwo=1, userThree=1, maxQueueBeforeBlockInPercent = 50%/3 = 17%
      queueRequest.decrement(userOne)
      //cannot add second userThree request because userThree occupied 25% of the queue
      increment(userThree, canBeAdded = false, queueSize = 3, userQueueSize = 1)
      //add first userFour request  queueSize = 4, userOne in queue = 1, userTwo=1, userThree=1, userFour=1, maxQueueBeforeBlockInPercent = 50%/4 = 12%
      increment(userFour, canBeAdded = true, queueSize = 4, userQueueSize = 1)
      //userOne finished one request so queueSize = 3, userOne in queue = 0 and is removed, userTwo=1, userThree=1, userFour=1, maxQueueBeforeBlockInPercent = 50%/3 = 17%
      queueRequest.decrement(userOne)
      //cannot add next userThree request because userThree occupied 25% of the queue
      increment(userThree, canBeAdded = false, queueSize = 3, userQueueSize = 1)
      //add next userOne request - queueSize = 4, userOne in queue = 1, userTwo=1, userThree=1, userFour=1, maxQueueBeforeBlockInPercent = 50%/4 = 12%
      increment(userOne, canBeAdded = true, queueSize = 4, userQueueSize = 1)
      //cannot add first userFive request because the queue is full
      increment(userFive, canBeAdded = false, queueSize = 4, userQueueSize = 0)
      //userOne finished one request so queueSize = 3, userOne in queue = 0 and is removed, userTwo=1, userThree=1, userFour=1, maxQueueBeforeBlockInPercent = 50%/3 = 17%
      queueRequest.decrement(userOne)
      //add first userFive request - queueSize = 4 userTwo=1, userThree=1, userFour=1,userFive=1 maxQueueBeforeBlockInPercent = 50%/4 = 12%
      increment(userFive, canBeAdded = true, queueSize = 4, userQueueSize = 1)
      //userTwo finished one request so queueSize = 3 userTwo=0 and is removed, userThree=1, userFour=1,userFive=1 maxQueueBeforeBlockInPercent = 50%/3 = 17%
      queueRequest.decrement(userTwo)
      //userThree finished one request so queueSize = 2 userThree=0 and is removed, userFour=1,userFive=1 maxQueueBeforeBlockInPercent = 50%/2 = 25%
      queueRequest.decrement(userThree)
      //userFour finished one request so queueSize = 1 userFour=0 and is removed,userFive=1 maxQueueBeforeBlockInPercent = 50%/1 = 50%
      queueRequest.decrement(userFour)
      //add next userFive request - queueSize = 2 userFive=2 maxQueueBeforeBlockInPercent = 50%/1 = 50%
      increment(userFive, canBeAdded = true, queueSize = 2, userQueueSize = 2)
      //cannot add next userFive request because userThree occupied 50% of the queue
      increment(userFive, canBeAdded = false, queueSize = 2, userQueueSize = 2)
      //add next userOne request - queueSize = 3, userOne in queue = 1, userFive=2, maxQueueBeforeBlockInPercent = 50%/2 = 25%
      increment(userOne, canBeAdded = true, queueSize = 3, userQueueSize = 1)
      //userFive finished one request so queueSize = 2, userOne in queue = 1, userFive=1, maxQueueBeforeBlockInPercent = 50%/2 = 25%
      queueRequest.decrement(userFive)
      //userFive finished one request so queueSize = 1, userOne in queue = 1, userFive=0 and is removed, maxQueueBeforeBlockInPercent = 50%/1 = 50%
      queueRequest.decrement(userFive)
      //userOne finished one request so queueSize = 0, userOne in queue = 0 and is removed
      queueRequest.decrement(userOne)
    }
  }

  private def increment(user: User, canBeAdded: Boolean, queueSize: Int, userQueueSize: Int) {
    assert(queueRequest.addIfAllowedUserToRequestQueue(user) == canBeAdded)
    assert(queueRequest.getQueue.get() == queueSize)
    assert(queueRequest.getUserQueue(user.userName.value).get == userQueueSize)
  }

}
