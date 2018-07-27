package com.ing.wbaa.gargoyle.proxy

import akka.actor.ActorSystem
import com.ing.wbaa.gargoyle.proxy.config.{GargoyleHttpSettings, GargoyleRangerSettings, GargoyleStorageS3Settings}
import com.ing.wbaa.gargoyle.proxy.handler.RequestHandlerS3
import com.ing.wbaa.gargoyle.proxy.providers.AuthorizationProviderRanger

object Server extends App {

  new GargoyleS3Proxy with AuthorizationProviderRanger with RequestHandlerS3 {
    override implicit lazy val system: ActorSystem = ActorSystem.create("gargoyle-s3proxy")
    override val httpSettings = GargoyleHttpSettings(system)
    override val rangerSettings = GargoyleRangerSettings(system)
    override val storageS3Settings = GargoyleStorageS3Settings(system)
  }.bind

}
