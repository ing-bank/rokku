package com.ing.wbaa.rokku.proxy.data

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.{ DefaultJsonProtocol, RootJsonFormat }

trait JsonProtocols extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val userRawJsonFormat: RootJsonFormat[UserRawJson] = jsonFormat5(UserRawJson)
}
