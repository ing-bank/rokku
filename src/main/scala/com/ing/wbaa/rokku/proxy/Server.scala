package com.ing.wbaa.rokku.proxy

import akka.actor.{ ActorSystem, Props }
import com.ing.wbaa.rokku.proxy.config._
import com.ing.wbaa.rokku.proxy.handler.parsers.RequestParser
import com.ing.wbaa.rokku.proxy.handler.{ FilterRecursiveListBucketHandler, RequestHandlerS3 }
import com.ing.wbaa.rokku.proxy.persistence.HttpRequestRecorder
import com.ing.wbaa.rokku.proxy.provider._
import com.ing.wbaa.rokku.proxy.queue.MemoryUserRequestQueue
import com.typesafe.config.ConfigFactory

object Server extends App {

  new RokkuS3Proxy with AuthorizationProviderRanger with RequestHandlerS3 with AuthenticationProviderSTS with LineageProviderAtlas with SignatureProviderAws with KerberosLoginProvider with FilterRecursiveListBucketHandler with MessageProviderKafka with AuditLogProvider with MemoryUserRequestQueue with RequestParser {

    override implicit lazy val system: ActorSystem = ActorSystem.create("rokku")

    override def kerberosSettings: KerberosSettings = KerberosSettings(system)

    override val httpSettings: HttpSettings = HttpSettings(system)
    override val rangerSettings: RangerSettings = RangerSettings(system)
    override val storageS3Settings: StorageS3Settings = StorageS3Settings(system)
    override val stsSettings: StsSettings = StsSettings(system)
    override val kafkaSettings: KafkaSettings = KafkaSettings(system)

    val requestPersistenceEnabled = ConfigFactory.load().getBoolean("rokku.requestPersistence.enabled")
    val configuredPersistenceId = ConfigFactory.load().getString("rokku.requestPersistence.persistenceId")

    if (requestPersistenceEnabled) {
      system.actorOf(Props(classOf[HttpRequestRecorder]), configuredPersistenceId)
    }

    // Force Ranger plugin to initialise on startup
    rangerPluginForceInit
  }.startup

}
