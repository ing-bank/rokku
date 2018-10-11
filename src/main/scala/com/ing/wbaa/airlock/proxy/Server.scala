package com.ing.wbaa.airlock.proxy

import akka.actor.ActorSystem
import com.ing.wbaa.airlock.proxy.config._
import com.ing.wbaa.airlock.proxy.handler.RequestHandlerS3
import com.ing.wbaa.airlock.proxy.provider.{ AuthenticationProviderSTS, AuthorizationProviderRanger, LineageProviderAtlas, SignatureProviderAws }

object Server extends App {

  new AirlockS3Proxy with AuthorizationProviderRanger with RequestHandlerS3 with AuthenticationProviderSTS with LineageProviderAtlas with SignatureProviderAws {
    override implicit lazy val system: ActorSystem = ActorSystem.create("airlock")
    override val httpSettings = HttpSettings(system)
    override val rangerSettings = RangerSettings(system)
    override val storageS3Settings = StorageS3Settings(system)
    override val stsSettings: StsSettings = StsSettings(system)
    override val atlasSettings: AtlasSettings = AtlasSettings(system)

    // Force Ranger plugin to initialise on startup
    rangerPluginForceInit
  }.startup

}
