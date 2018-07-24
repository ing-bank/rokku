package nl.wbaa.gargoyle.proxy
package config

import akka.actor.{ ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import com.typesafe.config.Config

class GargoyleSettings(config: Config) extends Extension {
  val httpPort: Int = config.getInt("gargoyle.http.port")
  val httpBind: String = config.getString("gargoyle.http.bind")
}

object GargoyleSettings extends ExtensionId[GargoyleSettings] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): GargoyleSettings = new GargoyleSettings(system.settings.config)
  override def lookup(): ExtensionId[GargoyleSettings] = GargoyleSettings
}
