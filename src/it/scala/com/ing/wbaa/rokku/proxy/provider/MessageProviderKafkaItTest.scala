package com.ing.wbaa.rokku.proxy.provider

import java.net.InetAddress

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpMethods, RemoteAddress}
import com.ing.wbaa.rokku.proxy.config.KafkaSettings
import com.ing.wbaa.rokku.proxy.data._
import com.ing.wbaa.rokku.proxy.handler.parsers.RequestParser.RequestTypeUnknown
import net.manub.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.scalatest.RecoverMethods._
import org.scalatest.{DiagrammedAssertions, WordSpecLike}

import scala.concurrent.ExecutionContext

class MessageProviderKafkaItTest extends WordSpecLike with DiagrammedAssertions with EmbeddedKafka with MessageProviderKafka {

  implicit val testSystem: ActorSystem = ActorSystem("kafkaTest")

  private val testKafkaPort = 9093

  override implicit val kafkaSettings: KafkaSettings = new KafkaSettings(testSystem.settings.config) {
    override val bootstrapServers: String = s"localhost:$testKafkaPort"
  }

  override implicit val executionContext: ExecutionContext = testSystem.dispatcher

  implicit val requestId: RequestId = RequestId("test")

  val s3Request = S3Request(AwsRequestCredential(AwsAccessKey("a"), None), Some("demobucket"), Some("s3object"), Read())
    .copy(clientIPAddress = RemoteAddress(InetAddress.getByName("127.0.0.1")))

  "KafkaMessageProvider" should {
    "Send message to correct topic with Put or Post" in {
      implicit val config = EmbeddedKafkaConfig(kafkaPort = testKafkaPort)

      withRunningKafka {
        Thread.sleep(3000)
        val createEventsTopic = "create_events"
        createCustomTopic(createEventsTopic)
        emitEvent(s3Request, HttpMethods.PUT, "testUser", RequestTypeUnknown())
        val result = consumeFirstStringMessageFrom(createEventsTopic)
        assert(result.contains("s3:ObjectCreated:PUT"))
      }
    }

    "Send message to correct topic with Delete" in {
      implicit val config = EmbeddedKafkaConfig(kafkaPort = testKafkaPort)

      withRunningKafka {
        Thread.sleep(3000)
        val deleteEventsTopic = "delete_events"
        createCustomTopic(deleteEventsTopic)
        emitEvent(s3Request, HttpMethods.DELETE, "testUser", RequestTypeUnknown())
        assert(consumeFirstStringMessageFrom(deleteEventsTopic).contains("s3:ObjectRemoved:DELETE"))
      }
    }

    "fail on incomplete data" in {
      recoverToSucceededIf[Exception](emitEvent(s3Request.copy(s3Object = None), HttpMethods.PUT, "testUser", RequestTypeUnknown()))
    }
  }

}
