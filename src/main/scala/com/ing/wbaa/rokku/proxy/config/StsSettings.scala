package com.ing.wbaa.rokku.proxy.config

import akka.actor.{ ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import akka.http.scaladsl.model.Uri
import com.typesafe.config.Config

class StsSettings(config: Config) extends Extension {
  private val stsUri: String = config.getString("rokku.sts.uri")
  val stsBaseUri: Uri = Uri(stsUri)
  val encodeSecret: String = config.getString("rokku.sts.encodeSecret")
  val cacheTTLInSeconds: Int = config.getInt("rokku.sts.cache.ttlInSeconds")

}

object StsSettings extends ExtensionId[StsSettings] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): StsSettings = new StsSettings(system.settings.config)
  override def lookup(): ExtensionId[StsSettings] = StsSettings
}
