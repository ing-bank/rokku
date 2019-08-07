package com.ing.wbaa.rokku.proxy.queue

import com.ing.wbaa.rokku.proxy.data.{ RequestId, User }

trait UserRequestQueue {

  /**
   *
   * @param user
   * @return true if the user is added to the queue
   */
  def addIfAllowedUserToRequestQueue(user: User)(implicit id: RequestId): Boolean

  /**
   * Remove the user from the queue
   * @param user
   */
  def decrement(user: User)(implicit id: RequestId): Unit
}
