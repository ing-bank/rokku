package com.ing.wbaa.rokku.proxy.data

import org.scalatest.{ DiagrammedAssertions, WordSpec }

class JsonProtocolsSpec extends WordSpec with DiagrammedAssertions with JsonProtocols {

  import spray.json._

  "Json protocols" should {
    "parse a User" that {
      "has a group" in {
        val jsonString =
          """{
            | "userName": "user",
            | "userGroups": ["group1"],
            | "accessKey": "accesskey",
            | "secretKey": "secretkey"
            |}""".stripMargin
        val result = jsonString.parseJson.convertTo[UserRawJson]
        assert(result == UserRawJson("user", Some(Set("group1")), "accesskey", "secretkey", None))
      }

      "does not have a group" in {
        val jsonString =
          """{
            | "userName": "user",
            | "userGroups": [],
            | "accessKey": "accesskey",
            | "secretKey": "secretkey"
            |}""".stripMargin
        val result = jsonString.parseJson.convertTo[UserRawJson]
        assert(result == UserRawJson("user", Some(Set.empty[String]), "accesskey", "secretkey", None))
      }

      "fail when fields are missing" in {
        val jsonString =
          """{
            | "userName": "user"
            |}""".stripMargin

        assertThrows[spray.json.DeserializationException] {
          jsonString.parseJson.convertTo[UserRawJson]
        }
      }
    }
  }
}
