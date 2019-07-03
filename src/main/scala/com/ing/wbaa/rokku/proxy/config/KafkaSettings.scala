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
  val protocol: String = config.getString("kafka.producer.protocol")
  val maxblock: String = config.getString("kafka.producer.maxblock")
  val kafkaConfig = config.getConfig("kafka.producer")
}

object KafkaSettings extends ExtensionId[KafkaSettings] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): KafkaSettings = new KafkaSettings(system.settings.config)
  override def lookup(): ExtensionId[KafkaSettings] = KafkaSettings
}
