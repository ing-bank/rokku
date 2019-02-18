package com.ing.wbaa.airlock.proxy.provider.kafka

import akka.Done
import akka.stream.ActorMaterializer
import com.ing.wbaa.airlock.proxy.config.KafkaSettings
import org.apache.kafka.clients.producer.{ KafkaProducer, ProducerConfig, ProducerRecord }
import org.apache.kafka.common.serialization.StringSerializer

import scala.concurrent.duration.SECONDS
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

trait EventProducer {
  import scala.collection.JavaConverters._

  protected[this] implicit val kafkaSettings: KafkaSettings

  protected[this] implicit val materializer: ActorMaterializer

  protected[this] implicit val executionContext: ExecutionContext

  private lazy val config: Map[String, Object] =
    Map[String, Object](
      "bootstrap.servers" -> kafkaSettings.bootstrapServers,
      ProducerConfig.RETRIES_CONFIG -> kafkaSettings.retries,
      ProducerConfig.RECONNECT_BACKOFF_MS_CONFIG -> kafkaSettings.retriesBackOff,
      ProducerConfig.RECONNECT_BACKOFF_MAX_MS_CONFIG -> kafkaSettings.retriesBackOffMax
    )

  def kafkaProducer: KafkaProducer[String, String] = new KafkaProducer(config.asJava, new StringSerializer, new StringSerializer)

  def sendSingleMessage(event: String, topic: String): Future[Done] = {
    val sentF = kafkaProducer.send(new ProducerRecord[String, String](topic, event))
    Try(
      sentF.get(3, SECONDS)
    ) match {
        case Success(r) =>
          kafkaProducer.close()
          Future.successful(Done)
        case Failure(ex) => Future.failed(throw new Exception(s"Failed to send event to Kafka, ${ex.getMessage}"))
      }
  }
}
