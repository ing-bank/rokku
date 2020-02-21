package com.ing.wbaa.rokku.proxy.data

import org.scalatest.diagrams.Diagrams
import org.scalatest.wordspec.AnyWordSpec

class UserSpec extends AnyWordSpec with Diagrams {
  "UserRawJson" should {
    "convert to User in apply of UserRawJson" in {
      assert(
        User(UserRawJson("u", Some(Set("g")), "a", "s", None)) ==
          User(UserName("u"), Set(UserGroup("g")), AwsAccessKey("a"), AwsSecretKey("s"), UserAssumeRole(""))
      )
      assert(
        User(UserRawJson("u", None, "a", "s", Some("testrole"))) ==
          User(UserName("u"), Set(), AwsAccessKey("a"), AwsSecretKey("s"), UserAssumeRole("testrole"))
      )
    }
  }
}
