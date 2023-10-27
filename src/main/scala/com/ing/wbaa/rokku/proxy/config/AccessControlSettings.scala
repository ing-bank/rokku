package com.ing.wbaa.rokku.proxy.config

import akka.actor.{ ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import com.typesafe.config.Config

class AccessControlSettings(config: Config) extends Extension {
  val serviceType: String = config.getString("rokku.access-control.service_type")
  val appId: String = config.getString("rokku.access-control.app_id")
  val listBucketsEnabled: Boolean = config.getBoolean("rokku.access-control.allow-list-buckets")
  val createDeleteBucketsEnabled: Boolean = config.getBoolean("rokku.access-control.allow-create-delete-buckets")
  val userDomainPostfix: String = config.getString("rokku.access-control.user-domain-postfix")
  val auditEnabled: Boolean = config.getBoolean("rokku.access-control.enabled-audit")
  val rolePrefix: String = config.getString("rokku.access-control.role-prefix")
  val className: String = config.getString("rokku.access-control.class-name")
}

object AccessControlSettings extends ExtensionId[AccessControlSettings] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): AccessControlSettings = new AccessControlSettings(system.settings.config)
  override def lookup: ExtensionId[AccessControlSettings] = AccessControlSettings
}

