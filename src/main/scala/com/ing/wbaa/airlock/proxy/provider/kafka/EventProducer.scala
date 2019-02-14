package com.ing.wbaa.airlock.proxy.provider.kafka

import akka.Done
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.Producer
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import com.ing.wbaa.airlock.proxy.config.KafkaSettings
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer

import scala.concurrent.Future

trait EventProducer {

  protected[this] implicit def kafkaSettings: KafkaSettings

  protected[this] implicit val materializer: ActorMaterializer

  lazy val producerSettings =
    ProducerSettings(kafkaSettings.kafkaConfig, new StringSerializer, new StringSerializer)
      .withBootstrapServers(kafkaSettings.bootstrapServers)

  // Retry handling for producers is built-in into Kafka. In case of failure when sending a message,
  // an exception will be thrown, which should fail the stream.
  def sendSingleMessage(event: String, topic: String, prodSettings: ProducerSettings[String, String] = producerSettings): Future[Done] =
    Source.single(event)
      .map(value => new ProducerRecord[String, String](topic, value))
      .runWith(Producer.plainSink(prodSettings))

}
