package com.ing.wbaa.rokku.proxy.data

import org.scalatest.{ DiagrammedAssertions, WordSpec }

class UserSpec extends WordSpec with DiagrammedAssertions {
  "UserRawJson" should {
    "convert to User in apply of UserRawJson" in {
      assert(
        User(UserRawJson("u", Some(Set("g")), "a", "s", None)) ==
          User(UserName("u"), Set(UserGroup("g")), AwsAccessKey("a"), AwsSecretKey("s"), UserAssumeRole(""))
      )
    }
  }
}
