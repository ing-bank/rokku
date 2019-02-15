package com.ing.wbaa.airlock.proxy.provider

import java.net.InetAddress

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpMethods, RemoteAddress}
import akka.stream.ActorMaterializer
import com.ing.wbaa.airlock.proxy.config.KafkaSettings
import com.ing.wbaa.airlock.proxy.data.{AwsAccessKey, AwsRequestCredential, Read, S3Request}
import net.manub.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.scalatest.{DiagrammedAssertions, WordSpecLike}

class MessageProviderKafkaItTest extends WordSpecLike with DiagrammedAssertions with EmbeddedKafka with MessageProviderKafka {

  implicit val testSystem: ActorSystem = ActorSystem("kafkaTest")

  override implicit def kafkaSettings: KafkaSettings = new KafkaSettings(testSystem.settings.config)

  override implicit val materializer: ActorMaterializer = ActorMaterializer()

  val s3Request = S3Request(AwsRequestCredential(AwsAccessKey("a"), None), Some("demobucket"), Some("s3object"), Read)
  val remoteClientIP = RemoteAddress(InetAddress.getByName("127.0.0.1"))

  "KafkaMessageProvider" should {
    "Send message to correct topic with Put or Post" in {
      implicit val config = EmbeddedKafkaConfig(kafkaPort = 9092)

      withRunningKafka {
        val createEventsTopic = "create_events"
        createCustomTopic(createEventsTopic)

        emitEvent(s3Request, HttpMethods.PUT, "testUser", remoteClientIP)
        val result = consumeFirstStringMessageFrom(createEventsTopic)
        assert(result.contains("s3:ObjectCreated:PUT"))
      }
    }

    "Send message to correct topic with Delete" in {
      implicit val config = EmbeddedKafkaConfig(kafkaPort = 9092)

      withRunningKafka {
        val deleteEventsTopic = "delete_events"
        createCustomTopic(deleteEventsTopic)

        emitEvent(s3Request, HttpMethods.DELETE, "testUser", remoteClientIP)
        assert(consumeFirstStringMessageFrom(deleteEventsTopic).contains("s3:ObjectCreated:DELETE"))
      }
    }

    "fail on incomplete data" in {
      assertThrows[Exception](emitEvent(s3Request.copy(s3Object = None), HttpMethods.PUT, "testUser", remoteClientIP))
    }
  }

}
