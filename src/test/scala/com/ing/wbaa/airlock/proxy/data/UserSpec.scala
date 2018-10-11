package com.ing.wbaa.airlock.proxy.data

import org.scalatest.{ DiagrammedAssertions, WordSpec }

class UserSpec extends WordSpec with DiagrammedAssertions {
  "UserRawJson" should {
    "convert to User in apply of UserRawJson" in {
      assert(
        User(UserRawJson("u", Some("g"), "a", "s")) ==
          User(UserName("u"), Some(UserAssumedGroup("g")), AwsAccessKey("a"), AwsSecretKey("s"))
      )
    }
  }
}
