package com.ing.wbaa.gargoyle.proxy.config

import akka.actor.{ ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import akka.http.scaladsl.model.Uri
import com.typesafe.config.Config

class GargoyleStsSettings(config: Config) extends Extension {
  private val stsPort: Int = config.getInt("gargoyle.sts.port")
  private val stsHost: String = config.getString("gargoyle.sts.host")
  val stsBaseUri: Uri = Uri(
    scheme = "http",
    authority = Uri.Authority(host = Uri.Host(stsHost), port = stsPort)
  )
}

object GargoyleStsSettings extends ExtensionId[GargoyleStsSettings] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): GargoyleStsSettings = new GargoyleStsSettings(system.settings.config)
  override def lookup(): ExtensionId[GargoyleStsSettings] = GargoyleStsSettings
}
