package com.ing.wbaa.gargoyle.proxy.data

import org.scalatest.{ DiagrammedAssertions, WordSpec }

class JsonProtocolsSpec extends WordSpec with DiagrammedAssertions with JsonProtocols {

  import spray.json._

  "Json protocols" should {
    "parse a User" that {
      "has a group" in {
        val jsonString =
          """{
            | "userName": "user",
            | "userGroups": "group1"
            |}""".stripMargin
        val result = jsonString.parseJson.convertTo[User]
        assert(result == User("user", Some("group1")))
      }

      "does not have a group" in {
        val jsonString =
          """{
            | "userName": "user"
            |}""".stripMargin
        val result = jsonString.parseJson.convertTo[User]
        assert(result == User("user", None))
      }
    }
  }
}
