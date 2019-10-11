package com.ing.wbaa.rokku.proxy.config

import akka.actor.{ ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import com.typesafe.config.Config

class RangerSettings(config: Config) extends Extension {
  val serviceType: String = config.getString("rokku.ranger.service_type")
  val appId: String = config.getString("rokku.ranger.app_id")
  val listBucketsEnabled: Boolean = config.getBoolean("rokku.ranger.allow-list-buckets")
  val createBucketsEnabled: Boolean = config.getBoolean("rokku.ranger.allow-create-buckets")
  val userDomainPostfix: String = config.getString("rokku.ranger.user-domain-postfix")
  val auditEnabled: Boolean = config.getBoolean("rokku.ranger.enabled-audit")
  val rolePrefix: String = config.getString("rokku.ranger.role-prefix")
}

object RangerSettings extends ExtensionId[RangerSettings] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): RangerSettings = new RangerSettings(system.settings.config)
  override def lookup(): ExtensionId[RangerSettings] = RangerSettings
}

