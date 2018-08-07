package com.ing.wbaa.gargoyle.proxy.data

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.{ DefaultJsonProtocol, RootJsonFormat }

trait JsonProtocols extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val userFormat: RootJsonFormat[User] =
    jsonFormat(User.apply, "userId", "secretKey", "groups", "arn")
}
