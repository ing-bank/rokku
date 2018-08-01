package com.ing.wbaa.gargoyle.proxy.config

import akka.actor.{ ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import com.typesafe.config.Config

class GargoyleStsSettings(config: Config) extends Extension {
  val stsPort: Int = config.getInt("gargoyle.sts.port")
  val stsHost: String = config.getString("gargoyle.sts.host")
}

object GargoyleStsSettings extends ExtensionId[GargoyleStsSettings] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): GargoyleStsSettings = new GargoyleStsSettings(system.settings.config)
  override def lookup(): ExtensionId[GargoyleStsSettings] = GargoyleStsSettings
}
