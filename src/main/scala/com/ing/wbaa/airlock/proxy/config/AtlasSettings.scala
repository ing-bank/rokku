package com.ing.wbaa.airlock.proxy.config

import akka.actor.{ ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import akka.http.scaladsl.model.Uri
import com.typesafe.config.Config

class AtlasSettings(config: Config) extends Extension {
  val atlasApiHost: String = config.getString("airlock.atlas.host")
  val atlasApiPort: Int = config.getInt("airlock.atlas.port")
  val atlasApiUser: String = config.getString("airlock.atlas.user")
  val atlasApiPassword: String = config.getString("airlock.atlas.password")
  val atlasEnabled: Boolean = config.getBoolean("airlock.atlas.enabled")

  def atlasBaseUri: Uri = Uri(
    scheme = "http",
    authority = Uri.Authority(host = Uri.Host(atlasApiHost), port = atlasApiPort)
  )
}

object AtlasSettings extends ExtensionId[AtlasSettings] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): AtlasSettings = new AtlasSettings(system.settings.config)
  override def lookup(): ExtensionId[AtlasSettings] = AtlasSettings
}

