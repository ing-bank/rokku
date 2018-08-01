package com.ing.wbaa.gargoyle.proxy.data

import spray.json._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport

trait JsonProtocols extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val userFormat: RootJsonFormat[User] =
    jsonFormat(User.apply, "userId", "secretKey", "groups", "arn")
}
