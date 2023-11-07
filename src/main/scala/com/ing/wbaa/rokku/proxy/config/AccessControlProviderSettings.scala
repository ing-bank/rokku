package com.ing.wbaa.rokku.proxy.config

import akka.actor.{ ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import com.typesafe.config.{ Config, ConfigFactory }

import scala.jdk.CollectionConverters._

class AccessControlProviderSettings(config: Config) extends Extension {
  val listBucketsEnabled: Boolean = config.getBoolean("rokku.access-control.allow-list-buckets")
  val createDeleteBucketsEnabled: Boolean = config.getBoolean("rokku.access-control.allow-create-delete-buckets")
  val auditEnabled: Boolean = config.getBoolean("rokku.access-control.enabled-audit")
  val className: String = config.getString("rokku.access-control.class-name")
  val pluginParams: Map[String, String] = ConfigFactory.parseString(config.getString("rokku.access-control.plugin-params"))
    .entrySet().asScala.map(e => e.getKey -> e.getValue.unwrapped().toString).toMap
}

object AccessControlProviderSettings extends ExtensionId[AccessControlProviderSettings] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): AccessControlProviderSettings = new AccessControlProviderSettings(system.settings.config)

  override def lookup: ExtensionId[AccessControlProviderSettings] = AccessControlProviderSettings
}

