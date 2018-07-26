package com.ing.wbaa.gargoyle.proxy.config

import akka.actor.{ ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import com.typesafe.config.Config

class GargoyleRangerSettings(config: Config) extends Extension {
  val serviceType: String = config.getString("gargoyle.ranger.service_type")
  val appId: String = config.getString("gargoyle.ranger.app_id")
}

object GargoyleRangerSettings extends ExtensionId[GargoyleRangerSettings] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): GargoyleRangerSettings = new GargoyleRangerSettings(system.settings.config)
  override def lookup(): ExtensionId[GargoyleRangerSettings] = GargoyleRangerSettings
}

