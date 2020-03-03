package com.ing.wbaa.rokku.proxy.config

import akka.actor.{ ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import com.typesafe.config.Config

class KafkaSettings(config: Config) extends Extension {
  val bootstrapServers: String = config.getString("kafka.producer.bootstrapServers")
  val createEventsTopic: String = config.getString("kafka.producer.createTopic")
  val deleteEventsTopic: String = config.getString("kafka.producer.deleteTopic")
  val auditEventsTopic: String = config.getString("kafka.producer.auditTopic")
  val retries: String = config.getString("kafka.producer.retries")
  val retriesBackOff: String = config.getString("kafka.producer.backoff")
  val retriesBackOffMax: String = config.getString("kafka.producer.backoffMax")
  val requestTimeoutMs: String = config.getString("kafka.producer.requestTimeoutMs")
  val protocol: String = config.getString("kafka.producer.protocol")
  val maxblock: String = config.getString("kafka.producer.maxblock")
  val sslTruststoreLocation: String = config.getString("kafka.producer.ssl.truststore.location")
  val sslTruststorePassword: String = config.getString("kafka.producer.ssl.truststore.password")
  val sslKeystoreLocation: String = config.getString("kafka.producer.ssl.keystore.location")
  val sslKeystorePassword: String = config.getString("kafka.producer.ssl.keystore.password")
  val sslKeyPassword: String = config.getString("kafka.producer.ssl.key.password")
  val kafkaConfig: Config = config.getConfig("kafka.producer")
}

object KafkaSettings extends ExtensionId[KafkaSettings] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): KafkaSettings = new KafkaSettings(system.settings.config)
  override def lookup(): ExtensionId[KafkaSettings] = KafkaSettings
}
