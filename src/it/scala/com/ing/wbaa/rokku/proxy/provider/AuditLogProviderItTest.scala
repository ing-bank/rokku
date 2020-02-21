package com.ing.wbaa.rokku.proxy.provider

import java.net.InetAddress

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, RemoteAddress, StatusCodes}
import com.ing.wbaa.rokku.proxy.config.KafkaSettings
import com.ing.wbaa.rokku.proxy.data._
import com.ing.wbaa.rokku.proxy.handler.parsers.RequestParser.RequestTypeUnknown
import net.manub.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.scalatest.diagrams.Diagrams
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.ExecutionContext

class AuditLogProviderItTest extends AnyWordSpecLike with Diagrams with EmbeddedKafka with AuditLogProvider {

  implicit val testSystem: ActorSystem = ActorSystem("kafkaTest")

  private val testKafkaPort = 9093

  override def auditEnabled = true

  override implicit val kafkaSettings: KafkaSettings = new KafkaSettings(testSystem.settings.config) {
    override val bootstrapServers: String = s"localhost:$testKafkaPort"
  }

  override implicit val executionContext: ExecutionContext = testSystem.dispatcher

  implicit val requestId: RequestId = RequestId("test")

  val s3Request = S3Request(AwsRequestCredential(AwsAccessKey("a"), None), Some("demobucket"), Some("s3object"), Read())
    .copy(headerIPs = HeaderIPs(Some(RemoteAddress(InetAddress.getByName("127.0.0.1"))),
      Some(Seq(RemoteAddress(InetAddress.getByName("1.1.1.1")))),
      Some(RemoteAddress(InetAddress.getByName("2.2.2.2")))))

  "AuditLogProvider" should {
    "send audit" in {
      implicit val config = EmbeddedKafkaConfig(kafkaPort = testKafkaPort)

      withRunningKafka {
        Thread.sleep(3000)
        val createEventsTopic = "audit_events"
        createCustomTopic(createEventsTopic)
        auditLog(s3Request, HttpRequest(HttpMethods.PUT, "http://localhost", Nil), "testUser", RequestTypeUnknown(), StatusCodes.Processing)
        val result = consumeFirstStringMessageFrom(createEventsTopic)
        assert(result.contains("\"eventName\":\"PUT\""))
        assert(result.contains("\"sourceIPAddress\":\"ClientIp=unknown|X-Real-IP=127.0.0.1|X-Forwarded-For=1.1.1.1|Remote-Address=2.2.2.2\""))
        assert(result.contains("\"x-amz-request-id\":\"test\""))
        assert(result.contains("\"principalId\":\"testUser\""))
      }
    }
  }

}
