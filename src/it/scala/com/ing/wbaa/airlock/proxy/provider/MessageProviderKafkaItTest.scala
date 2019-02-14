package com.ing.wbaa.airlock.proxy.provider

import java.net.InetAddress

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpMethods, RemoteAddress}
import akka.kafka.Subscriptions
import akka.kafka.scaladsl.Consumer
import akka.kafka.testkit.scaladsl.{EmbeddedKafkaLike, ScalatestKafkaSpec}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Keep, Sink}
import com.ing.wbaa.airlock.proxy.config.KafkaSettings
import com.ing.wbaa.airlock.proxy.data._
import net.manub.embeddedkafka.EmbeddedKafkaConfig
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.{DiagrammedAssertions, Matchers, WordSpecLike}

import scala.concurrent.duration._

abstract class SpecBase(kafkaPort: Int)
  extends ScalatestKafkaSpec(kafkaPort)
    with WordSpecLike
    with EmbeddedKafkaLike
    with Matchers
    with ScalaFutures
    with Eventually
    with DiagrammedAssertions
    with MessageProviderKafka


class MessageProviderKafkaItTest extends SpecBase(9092) {

  implicit val testSystem: ActorSystem = ActorSystem.create("test-system")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val patience = PatienceConfig(15.seconds, 1.second)

  override val kafkaSettings: KafkaSettings = new KafkaSettings(testSystem.settings.config) {
    override val bootstrapServers: String = "127.0.0.1:9092"
    override val kafkaEnabled: Boolean = true
  }

  val s3Request = S3Request(AwsRequestCredential(AwsAccessKey("a"), None), Some("demobucket"), Some("s3object"), Read)
  val remoteClientIP = RemoteAddress(InetAddress.getByName("127.0.0.1"))

  def createKafkaConfig: EmbeddedKafkaConfig =
    EmbeddedKafkaConfig(kafkaPort,
      zooKeeperPort,
      Map(
        "offsets.topic.replication.factor" -> "1"
      ))

  "KafkaMessageProvider" should {
    "Send message" in {
      val createEventsTopic = createTopic(0,1,1)
      val (control2, result) = Consumer
        .plainSource(consumerDefaults.withGroupId(createGroupId()), Subscriptions.topics(createEventsTopic))
        .toMat(Sink.seq)(Keep.both)
        .run()

      sleep(6.seconds)
      sendSingleMessage("messagePublished", createEventsTopic, producerDefaults).futureValue should be(Done)
    }

    "fail on incomplete data" in {
      assertThrows[Exception](emitEvent(s3Request.copy(s3Object = None), HttpMethods.PUT, "testUser", remoteClientIP))
    }
  }


}
