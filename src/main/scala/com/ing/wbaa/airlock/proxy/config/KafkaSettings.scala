package com.ing.wbaa.airlock.proxy.config

import akka.actor.{ ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import com.typesafe.config.Config

class KafkaSettings(config: Config) extends Extension {
  val kafkaEnabled: Boolean = config.getBoolean("kafka.producer.enabled")
  val bootstrapServers: String = config.getString("kafka.producer.bootstrapServers")
  val createEventsTopic: String = config.getString("kafka.producer.createTopic")
  val deleteEventsTopic: String = config.getString("kafka.producer.deleteTopic")
  val retries: String = config.getString("kafka.producer.retries")
  val retriesBackOff: String = config.getString("kafka.producer.backoff")
  val retriesBackOffMax: String = config.getString("kafka.producer.backoffMax")
  val kafkaConfig = config.getConfig("kafka.producer")
}

object KafkaSettings extends ExtensionId[KafkaSettings] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): KafkaSettings = new KafkaSettings(system.settings.config)
  override def lookup(): ExtensionId[KafkaSettings] = KafkaSettings
}
