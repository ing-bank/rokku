package com.ing.wbaa.gargoyle.proxy.config

import akka.actor.{ ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import akka.http.scaladsl.model.Uri
import com.typesafe.config.Config

class GargoyleAtlasSettings(config: Config) extends Extension {
  val atlasApiHost: String = config.getString("gargoyle.atlas.host")
  val atlasApiPort: Int = config.getInt("gargoyle.atlas.port")
  val atlasApiUser: String = config.getString("gargoyle.atlas.user")
  val atlasApiPassword: String = config.getString("gargoyle.atlas.password")
  val atlasEnabled: Boolean = config.getBoolean("gargoyle.atlas.enabled")

  def atlasBaseUri: Uri = Uri(
    scheme = "http",
    authority = Uri.Authority(host = Uri.Host(atlasApiHost), port = atlasApiPort)
  )
}

object GargoyleAtlasSettings extends ExtensionId[GargoyleAtlasSettings] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): GargoyleAtlasSettings = new GargoyleAtlasSettings(system.settings.config)
  override def lookup(): ExtensionId[GargoyleAtlasSettings] = GargoyleAtlasSettings
}

