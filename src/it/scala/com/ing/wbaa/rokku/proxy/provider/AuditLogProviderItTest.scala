package com.ing.wbaa.rokku.proxy.provider

import java.net.InetAddress

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, RemoteAddress}
import akka.stream.ActorMaterializer
import com.ing.wbaa.rokku.proxy.config.KafkaSettings
import com.ing.wbaa.rokku.proxy.data._
import net.manub.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.scalatest.{DiagrammedAssertions, WordSpecLike}

import scala.concurrent.ExecutionContext

class AuditLogProviderItTest extends WordSpecLike with DiagrammedAssertions with EmbeddedKafka with AuditLogProvider {

  implicit val testSystem: ActorSystem = ActorSystem("kafkaTest")

  private val testKafkaPort = 9093

  override def auditEnabled = true

  override implicit val kafkaSettings: KafkaSettings = new KafkaSettings(testSystem.settings.config) {
    override val bootstrapServers: String = s"localhost:$testKafkaPort"
  }

  override implicit val materializer: ActorMaterializer = ActorMaterializer()

  override implicit val executionContext: ExecutionContext = testSystem.dispatcher

  implicit val requestId: RequestId = RequestId("test")

  val s3Request = S3Request(AwsRequestCredential(AwsAccessKey("a"), None), Some("demobucket"), Some("s3object"), Read())
    .copy(clientIPAddress = RemoteAddress(InetAddress.getByName("127.0.0.1")))

  "AuditLogProvider" should {
    "send audit" in {
      implicit val config = EmbeddedKafkaConfig(kafkaPort = testKafkaPort)

      withRunningKafka {
        Thread.sleep(3000)
        val createEventsTopic = "audit_events"
        createCustomTopic(createEventsTopic)
        auditLog(s3Request, HttpRequest(HttpMethods.PUT, "http://localhost", Nil), "testUser")
        val result = consumeFirstStringMessageFrom(createEventsTopic)
        assert(result.contains("\"eventName\":\"PUT\""))
      }
    }
  }

}
