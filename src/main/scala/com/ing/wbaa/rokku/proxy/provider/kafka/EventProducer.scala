package com.ing.wbaa.rokku.proxy.provider.kafka

import akka.Done
import akka.http.scaladsl.model.HttpMethod
import com.ing.wbaa.rokku.proxy.config.KafkaSettings
import com.ing.wbaa.rokku.proxy.data.RequestId
import com.ing.wbaa.rokku.proxy.handler.LoggerHandlerWithId
import com.ing.wbaa.rokku.proxy.metrics.MetricsFactory
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.producer.{ KafkaProducer, ProducerConfig, ProducerRecord, RecordMetadata }
import org.apache.kafka.common.serialization.StringSerializer

import scala.concurrent.{ ExecutionContext, Future }

trait EventProducer {

  private val logger = new LoggerHandlerWithId

  import scala.jdk.CollectionConverters._

  protected[this] implicit val kafkaSettings: KafkaSettings

  protected[this] implicit val executionContext: ExecutionContext

  private lazy val config: Map[String, Object] =
    Map[String, Object](
      "bootstrap.servers" -> kafkaSettings.bootstrapServers,
      ProducerConfig.RETRIES_CONFIG -> kafkaSettings.retries,
      ProducerConfig.RECONNECT_BACKOFF_MS_CONFIG -> kafkaSettings.retriesBackOff,
      ProducerConfig.RECONNECT_BACKOFF_MAX_MS_CONFIG -> kafkaSettings.retriesBackOffMax,
      CommonClientConfigs.SECURITY_PROTOCOL_CONFIG -> kafkaSettings.protocol,
      ProducerConfig.MAX_BLOCK_MS_CONFIG -> kafkaSettings.maxblock,
      ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG -> kafkaSettings.requestTimeoutMs,
      "ssl.truststore.location" -> kafkaSettings.sslTruststoreLocation,
      "ssl.truststore.password" -> kafkaSettings.sslTruststorePassword,
      "ssl.keystore.location" -> kafkaSettings.sslKeystoreLocation,
      "ssl.keystore.password" -> kafkaSettings.sslKeystorePassword,
      "ssl.key.password" -> kafkaSettings.sslKeyPassword
    )

  private lazy val kafkaProducer: KafkaProducer[String, String] = new KafkaProducer(config.asJava, new StringSerializer, new StringSerializer)

  def sendSingleMessage(event: String, topic: String, httpMethod: Option[HttpMethod] = None)(implicit id: RequestId): Future[Done] = {
    kafkaProducer
      .send(new ProducerRecord[String, String](topic, event), (metadata: RecordMetadata, exception: Exception) => {
        exception match {
          case e: Exception =>
            MetricsFactory.incrementKafkaSendErrors()
            logger.error("error in sending event {} to topic {}, error={}", event, topic, e)
            throw new Exception(e)
          case _ =>
            httpMethod.foreach { m => MetricsFactory.incrementKafkaNotificationsSent(m) }
            logger.debug("Message sent {} to kafka, offset {}", event, metadata.offset())
        }
      }) match {
        case _ => Future(Done)
      }
  }
}
