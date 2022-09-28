package com.ing.wbaa.rokku.proxy.config

import akka.actor.{ ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import com.typesafe.config.Config

class HttpSettings(config: Config) extends Extension {
  val httpPort: Int = config.getInt("rokku.http.port")
  val httpBind: String = config.getString("rokku.http.bind")
}

object HttpSettings extends ExtensionId[HttpSettings] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): HttpSettings = new HttpSettings(system.settings.config)
  override def lookup: ExtensionId[HttpSettings] = HttpSettings
}
