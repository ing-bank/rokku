package com.ing.wbaa.rokku.proxy.data.queue

import com.ing.wbaa.rokku.proxy.data.{ AwsAccessKey, AwsSecretKey, RequestId, User, UserName }
import com.ing.wbaa.rokku.proxy.queue.MemoryUserRequestQueue
import org.scalatest.{ DiagrammedAssertions, WordSpec }

class MemoryUserRequestQueueTest extends WordSpec with DiagrammedAssertions {

  implicit val id: RequestId = RequestId("test")
  private val queueRequest = new MemoryUserRequestQueue {
    override protected val queueSize: Long = 4
    override protected val maxQueueBeforeBlockInPercent: Int = 50
  }
  private val userOne = User(UserName("userOne"), Set.empty, AwsAccessKey(""), AwsSecretKey(""))
  private val userTwo = User(UserName("userTwo"), Set.empty, AwsAccessKey(""), AwsSecretKey(""))
  private val userThree = User(UserName("userThree"), Set.empty, AwsAccessKey(""), AwsSecretKey(""))

  "MemoryUserRequestQueue" should {

    "increment/decrement queue" in {
      assert(queueRequest.addIfAllowedUserToRequestQueue(userOne))
      assert(queueRequest.getQueue.get() == 1)
      assert(queueRequest.getUserQueue(userOne.userName.value).get == 1)
      assert(queueRequest.addIfAllowedUserToRequestQueue(userOne))
      assert(queueRequest.getQueue.get() == 2)
      assert(queueRequest.getUserQueue(userOne.userName.value).get == 2)
      assert(queueRequest.addIfAllowedUserToRequestQueue(userTwo))
      assert(queueRequest.getQueue.get() == 3)
      assert(queueRequest.getUserQueue(userTwo.userName.value).get == 1)
      assert(!queueRequest.addIfAllowedUserToRequestQueue(userOne))
      assert(queueRequest.getQueue.get() == 3)
      assert(queueRequest.getUserQueue(userOne.userName.value).get == 2)
      assert(queueRequest.addIfAllowedUserToRequestQueue(userTwo))
      assert(queueRequest.getQueue.get() == 4)
      assert(queueRequest.getUserQueue(userTwo.userName.value).get == 2)
      assert(!queueRequest.addIfAllowedUserToRequestQueue(userThree))
      assert(queueRequest.getQueue.get() == 4)
      assert(queueRequest.getUserQueue(userThree.userName.value).get == 0)
      queueRequest.decrement(userOne)
      assert(queueRequest.addIfAllowedUserToRequestQueue(userThree))
      assert(queueRequest.getQueue.get() == 4)
      assert(queueRequest.getUserQueue(userThree.userName.value).get == 1)
      assert(!queueRequest.addIfAllowedUserToRequestQueue(userOne))
      assert(queueRequest.getQueue.get() == 4)
      assert(queueRequest.getUserQueue(userOne.userName.value).get == 1)
      queueRequest.decrement(userOne)
      assert(queueRequest.getQueue.get() == 3)
      assert(queueRequest.getUserQueue(userOne.userName.value).get == 0)
      assert(queueRequest.getUserQueue(userTwo.userName.value).get == 2)
      assert(queueRequest.getUserQueue(userThree.userName.value).get == 1)
      queueRequest.decrement(userTwo)
      assert(queueRequest.getQueue.get() == 2)
      assert(queueRequest.getUserQueue(userOne.userName.value).get == 0)
      assert(queueRequest.getUserQueue(userTwo.userName.value).get == 1)
      assert(queueRequest.getUserQueue(userThree.userName.value).get == 1)
      queueRequest.decrement(userTwo)
      assert(queueRequest.getQueue.get() == 1)
      assert(queueRequest.getUserQueue(userOne.userName.value).get == 0)
      assert(queueRequest.getUserQueue(userTwo.userName.value).get == 0)
      assert(queueRequest.getUserQueue(userThree.userName.value).get == 1)
      queueRequest.decrement(userThree)
      assert(queueRequest.getQueue.get() == 0)
      assert(queueRequest.getUserQueue(userOne.userName.value).get == 0)
      assert(queueRequest.getUserQueue(userTwo.userName.value).get == 0)
      assert(queueRequest.getUserQueue(userThree.userName.value).get == 0)
      queueRequest.decrement(userThree)
      assert(queueRequest.getQueue.get() == 0)
      assert(queueRequest.getUserQueue(userOne.userName.value).get == 0)
      assert(queueRequest.getUserQueue(userTwo.userName.value).get == 0)
      assert(queueRequest.getUserQueue(userThree.userName.value).get == 0)
    }
  }
}
