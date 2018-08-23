package com.ing.wbaa.gargoyle.proxy.data

import org.scalatest.{ DiagrammedAssertions, FlatSpec }

class JsonProtocolsSpec extends FlatSpec with DiagrammedAssertions with JsonProtocols {

  import spray.json._

  "Json protocols" should "parse a User" in {
    val jsonString =
      """{
        | "userName": "user",
        | "userGroups": ["group1", "group2"]
        |}""".stripMargin
    val result = jsonString.parseJson.convertTo[User]
    assert(result == User("user", Set("group1", "group2")))
  }

}
