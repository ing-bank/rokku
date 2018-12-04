package com.ing.wbaa.airlock.proxy.config

import akka.actor.{ ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import com.typesafe.config.Config

class KerberosSettings(config: Config) extends Extension {
  val keytab: String = config.getString("airlock.kerberos.keytab")
  val principal: String = config.getString("airlock.kerberos.principal")
}

object KerberosSettings extends ExtensionId[KerberosSettings] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): KerberosSettings = new KerberosSettings(system.settings.config)
  override def lookup(): ExtensionId[KerberosSettings] = KerberosSettings
}
