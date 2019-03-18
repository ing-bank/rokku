package com.ing.wbaa.airlock.proxy.provider

import java.net.InetAddress

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpMethods, RemoteAddress}
import akka.stream.ActorMaterializer
import com.ing.wbaa.airlock.proxy.config.KafkaSettings
import com.ing.wbaa.airlock.proxy.data._
import net.manub.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.scalatest.{DiagrammedAssertions, WordSpecLike}
import org.scalatest.RecoverMethods._

import scala.concurrent.ExecutionContext

class MessageProviderKafkaItTest extends WordSpecLike with DiagrammedAssertions with EmbeddedKafka with MessageProviderKafka {

  implicit val testSystem: ActorSystem = ActorSystem("kafkaTest")

  override implicit val kafkaSettings: KafkaSettings = KafkaSettings(testSystem)

  override implicit val materializer: ActorMaterializer = ActorMaterializer()

  override implicit val executionContext: ExecutionContext = testSystem.dispatcher

  implicit val requestId: RequestId = RequestId("test")

  val s3Request = S3Request(AwsRequestCredential(AwsAccessKey("a"), None), Some("demobucket"), Some("s3object"), Read)
    .copy(clientIPAddress = RemoteAddress(InetAddress.getByName("127.0.0.1")))

  "KafkaMessageProvider" should {
    "Send message to correct topic with Put or Post" in {
      implicit val config = EmbeddedKafkaConfig(kafkaPort = 9092)

      withRunningKafka {
        val createEventsTopic = "create_events"
        createCustomTopic(createEventsTopic)
        Thread.sleep(3000)
        emitEvent(s3Request, HttpMethods.PUT, "testUser")
        val result = consumeFirstStringMessageFrom(createEventsTopic)
        assert(result.contains("s3:ObjectCreated:PUT"))
      }
    }

    "Send message to correct topic with Delete" in {
      implicit val config = EmbeddedKafkaConfig(kafkaPort = 9092)

      withRunningKafka {
        val deleteEventsTopic = "delete_events"
        createCustomTopic(deleteEventsTopic)
        Thread.sleep(3000)
        emitEvent(s3Request, HttpMethods.DELETE, "testUser")
        assert(consumeFirstStringMessageFrom(deleteEventsTopic).contains("s3:ObjectRemoved:DELETE"))
      }
    }

    "fail on incomplete data" in {
      recoverToSucceededIf[Exception](emitEvent(s3Request.copy(s3Object = None), HttpMethods.PUT, "testUser"))
    }
  }

}
