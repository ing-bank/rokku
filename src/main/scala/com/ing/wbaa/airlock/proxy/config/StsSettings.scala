package com.ing.wbaa.airlock.proxy.config

import akka.actor.{ ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import akka.http.scaladsl.model.Uri
import com.typesafe.config.Config

class StsSettings(config: Config) extends Extension {
  private val stsPort: Int = config.getInt("airlock.sts.port")
  private val stsHost: String = config.getString("airlock.sts.host")
  val stsBaseUri: Uri = Uri(
    scheme = "http",
    authority = Uri.Authority(host = Uri.Host(stsHost), port = stsPort)
  )
}

object StsSettings extends ExtensionId[StsSettings] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): StsSettings = new StsSettings(system.settings.config)
  override def lookup(): ExtensionId[StsSettings] = StsSettings
}
