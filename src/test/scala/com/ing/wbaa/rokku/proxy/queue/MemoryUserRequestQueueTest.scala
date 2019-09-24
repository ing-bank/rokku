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
      increment(userOne, canBeAdded = true, queueSize = 1, userQueueSize = 1)
      increment(userOne, canBeAdded = true, queueSize = 2, userQueueSize = 2)
      increment(userTwo, canBeAdded = true, queueSize = 3, userQueueSize = 1)
      increment(userTwo, canBeAdded = false, queueSize = 3, userQueueSize = 1)
      increment(userOne, canBeAdded = false, queueSize = 3, userQueueSize = 2)
      increment(userThree, canBeAdded = true, queueSize = 4, userQueueSize = 1)
      increment(userThree, canBeAdded = false, queueSize = 4, userQueueSize = 1)
      queueRequest.decrement(userOne) //queueSize = 3, userOne = 1
      increment(userThree, canBeAdded = false, queueSize = 3, userQueueSize = 1)
      increment(userFour, canBeAdded = true, queueSize = 4, userQueueSize = 1)
      queueRequest.decrement(userOne) //queueSize = 3, userOne = 0
      increment(userThree, canBeAdded = false, queueSize = 3, userQueueSize = 1)
      increment(userOne, canBeAdded = true, queueSize = 4, userQueueSize = 1)
      increment(userFive, canBeAdded = false, queueSize = 4, userQueueSize = 0)
      queueRequest.decrement(userOne) //queueSize = 3, userOne = 1
      increment(userFive, canBeAdded = true, queueSize = 4, userQueueSize = 1)
      queueRequest.decrement(userTwo) //queueSize = 3, userTwo = 0
      queueRequest.decrement(userThree) //queueSize = 2, userThree = 0
      queueRequest.decrement(userFour) //queueSize = 1, userFour = 0
      increment(userFive, canBeAdded = true, queueSize = 2, userQueueSize = 2)
      increment(userFive, canBeAdded = false, queueSize = 2, userQueueSize = 2)
      increment(userOne, canBeAdded = true, queueSize = 3, userQueueSize = 1)
      queueRequest.decrement(userFive)
      queueRequest.decrement(userFive)
      queueRequest.decrement(userOne)
    }
  }

  private def increment(user: User, canBeAdded: Boolean, queueSize: Int, userQueueSize: Int) {
    assert(queueRequest.addIfAllowedUserToRequestQueue(user) == canBeAdded)
    assert(queueRequest.getQueue.get() == queueSize)
    assert(queueRequest.getUserQueue(user.userName.value).get == userQueueSize)
  }
}
