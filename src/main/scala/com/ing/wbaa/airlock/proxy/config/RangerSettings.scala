package com.ing.wbaa.airlock.proxy.config

import akka.actor.{ ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import com.typesafe.config.Config

class RangerSettings(config: Config) extends Extension {
  val serviceType: String = config.getString("airlock.ranger.service_type")
  val appId: String = config.getString("airlock.ranger.app_id")
  val listBucketsEnabled: Boolean = config.getBoolean("airlock.ranger.allow-list-buckets")
  val createBucketsEnabled: Boolean = config.getBoolean("airlock.ranger.allow-create-buckets")
  val userDomainPostfix: String = config.getString("airlock.ranger.user-domain-postfix")
  val auditEnabled = config.getBoolean("airlock.ranger.enabled-audit")
}

object RangerSettings extends ExtensionId[RangerSettings] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): RangerSettings = new RangerSettings(system.settings.config)
  override def lookup(): ExtensionId[RangerSettings] = RangerSettings
}

