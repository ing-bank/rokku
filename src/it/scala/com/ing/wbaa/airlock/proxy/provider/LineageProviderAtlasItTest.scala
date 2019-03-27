package com.ing.wbaa.airlock.proxy.provider

import java.net.InetAddress

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import com.ing.wbaa.airlock.proxy.config.{AtlasSettings, KafkaSettings}
import com.ing.wbaa.airlock.proxy.data._
import net.manub.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.scalatest.{Assertion, DiagrammedAssertions, WordSpecLike}

import scala.concurrent.ExecutionContext

class LineageProviderAtlasItTest extends WordSpecLike with DiagrammedAssertions with EmbeddedKafka {

  implicit val testSystem: ActorSystem = ActorSystem.create("test-system")
  implicit val requestId: RequestId = RequestId("test")

  def fakeIncomingHttpRequest(method: HttpMethod, path: String) = {
    val uri = Uri(
      scheme = "http",
      authority = Uri.Authority(host = Uri.Host("proxyHost"), port = 8010)).withPath(Uri.Path(path))

    method match {
      case HttpMethods.GET => HttpRequest(method, uri, Nil)
      case HttpMethods.POST | HttpMethods.PUT => HttpRequest(method, uri, Nil, HttpEntity(ContentTypes.`application/json`, "{}"))
      case HttpMethods.DELETE => HttpRequest(method, uri, Nil)
      case _ => HttpRequest(method, uri, Nil)
    }
  }

  val remoteClientIP = RemoteAddress(InetAddress.getByName("127.0.0.1"))

  val userSTS = User(UserName("fakeUser"), Set.empty[UserGroup], AwsAccessKey("a"), AwsSecretKey("k"))

  def withLineageProviderAtlas(atlasTestSettings: AtlasSettings = AtlasSettings(testSystem))(testCode: LineageProviderAtlas => Assertion) =
    testCode(new LineageProviderAtlas {
      override protected[this] implicit def system: ActorSystem = ActorSystem.create("test-system")

      override protected[this] implicit val executionContext: ExecutionContext = system.dispatcher

      override protected[this] implicit def atlasSettings: AtlasSettings = atlasTestSettings

      override protected[this] implicit val materializer: ActorMaterializer = ActorMaterializer()(system)

      override val kafkaSettings: KafkaSettings = KafkaSettings(system)
    })

  val createEventsTopic = "ATLAS_HOOK"

  "LineageProviderAtlas" should {
    "create Write lineage from HttpRequest" in withLineageProviderAtlas() { apr =>
      implicit val config = EmbeddedKafkaConfig(kafkaPort = 9092)
      withRunningKafka {
        createCustomTopic(createEventsTopic)
        Thread.sleep(1000)

        apr.createLineageFromRequest(
          fakeIncomingHttpRequest(HttpMethods.PUT, "/fakeBucket/fakeObject"), userSTS, remoteClientIP)
        Thread.sleep(1000)

        val message = consumeFirstStringMessageFrom(createEventsTopic)
        assert(message.contains("external_object_in/fakeObject"))
      }
    }

    "create Read lineage from HttpRequest" in withLineageProviderAtlas() { apr =>
      implicit val config = EmbeddedKafkaConfig(kafkaPort = 9092)
      withRunningKafka {

        apr.createLineageFromRequest(
          fakeIncomingHttpRequest(HttpMethods.GET, "/fakeBucket/fakeObject"), userSTS, remoteClientIP)
        Thread.sleep(1000)

        val message = consumeFirstStringMessageFrom(createEventsTopic)
        assert(message.contains("external_object_out/fakeObject"))
      }
    }

    "create Delete lineage from HttpRequest" in withLineageProviderAtlas() { apr =>
      implicit val config = EmbeddedKafkaConfig(kafkaPort = 9092)
      withRunningKafka {

        apr.createLineageFromRequest(
          fakeIncomingHttpRequest(HttpMethods.DELETE, "/fakeBucket/fakeObject"), userSTS, remoteClientIP)
        Thread.sleep(1000)

        val message = consumeFirstStringMessageFrom(createEventsTopic)
        assert(message.contains("fakeObject"))
      }
    }
  }
}
