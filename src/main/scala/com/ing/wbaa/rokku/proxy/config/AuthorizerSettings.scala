package com.ing.wbaa.rokku.proxy.config

import akka.actor.{ ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import com.typesafe.config.Config

class AuthorizerSettings(config: Config) extends Extension {
  val serviceType: String = config.getString("rokku.authorizer.service_type")
  val appId: String = config.getString("rokku.authorizer.app_id")
  val listBucketsEnabled: Boolean = config.getBoolean("rokku.authorizer.allow-list-buckets")
  val createDeleteBucketsEnabled: Boolean = config.getBoolean("rokku.authorizer.allow-create-delete-buckets")
  val userDomainPostfix: String = config.getString("rokku.authorizer.user-domain-postfix")
  val auditEnabled: Boolean = config.getBoolean("rokku.authorizer.enabled-audit")
  val rolePrefix: String = config.getString("rokku.authorizer.role-prefix")
}

object AuthorizerSettings extends ExtensionId[AuthorizerSettings] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): AuthorizerSettings = new AuthorizerSettings(system.settings.config)
  override def lookup: ExtensionId[AuthorizerSettings] = AuthorizerSettings
}

